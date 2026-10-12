package com.kntro.reqsai.billing;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.application.ai.AiUsageReporter;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubRequirementGenerationConfig;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The AI usage ledger, the monthly counter and the allowance that guards AI generation, against a real
 * database: each call is one idempotent ledger row whose quota weight is added to the counter atomically
 * (parallel increments sum exactly), warnings are pushed once per level and period, a spent allowance stops
 * AI generation with {@code AI_QUOTA_EXCEEDED} (402) but not reading, and a new period starts clean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubRequirementGenerationConfig.class)
@Tag("integration")
@DisplayName("Integration: AI usage metering and quota")
class AiUsageMeteringIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BillingModuleApi billing;
    @Autowired
    private AiQuotaGuard guard;
    @Autowired
    private AiUsageReporter reporter;
    @MockitoBean
    private RealtimeNotifier notifier;

    private record Org(UUID id, String slug, UUID projectId) {
        TenantContext.TenantSnapshot tenant() {
            return new TenantContext.TenantSnapshot(id.toString(), "tenant_" + slug);
        }
    }

    @Test
    @DisplayName("every call is one ledger row and one counter increment, and a repeated call counts once")
    void ledger_and_counter_agree_and_are_idempotent() {
        Org org = createOrg();
        TokenUsage first = TokenUsage.of(AiFeature.CHAT, org.projectId(), "gpt-4o-mini", 3_000, 500, 1_000, "call-1");
        TokenUsage second = TokenUsage.of(AiFeature.LIVE_SUGGESTIONS, org.projectId(), "gpt-4o-mini", 2_000, 100, 0, "call-2");

        record(org, first);
        record(org, second);
        record(org, first);

        // 3,000 input of which 1,000 cached: 2,000 + 500 (half of 1,000) + 500 output = 3,000; second = 2,100
        assertThat(counter(org)).isEqualTo(3_000 + 2_100);
        assertThat(ledgerSum(org, "quota_tokens")).isEqualTo(5_100);
        assertThat(ledgerCount(org)).isEqualTo(2);
        Map<String, Object> row = jdbc.queryForMap("SELECT input_tokens, output_tokens, cached_input_tokens, model, project_id"
                + " FROM \"tenant_" + org.slug() + "\".ai_usage_events WHERE idempotency_key = 'call-1'");
        assertThat(row).containsEntry("input_tokens", 3_000L).containsEntry("output_tokens", 500L)
                .containsEntry("cached_input_tokens", 1_000L).containsEntry("model", "gpt-4o-mini");
    }

    @Test
    @DisplayName("parallel increments sum exactly and the counter equals the ledger")
    void concurrent_increments_lose_nothing() throws Exception {
        Org org = createOrg();
        int threads = 16;
        int perThread = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int worker = t;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    record(org, TokenUsage.of(AiFeature.LIVE_SUGGESTIONS, org.projectId(), "m", 40, 10, 0,
                            "w" + worker + "-" + i + "-" + org.id()));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        long expected = (long) threads * perThread * 50;
        assertThat(counter(org)).isEqualTo(expected);
        assertThat(ledgerSum(org, "quota_tokens")).isEqualTo(expected);
        assertThat(ledgerCount(org)).isEqualTo((long) threads * perThread);
    }

    @Test
    @DisplayName("the reporter writes off the calling thread, under the caller's tenant and scope")
    void reporter_is_asynchronous_and_scoped() {
        Org org = createOrg();

        TenantContext.runWith(org.tenant(), () ->
                AiUsageScope.run(AiFeature.CHAT, org.projectId(), () ->
                        reporter.report(TokenUsage.of(AiFeature.OTHER, null, "m", 700, 70, 0, null))));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(counter(org)).isEqualTo(770));
        assertThat(jdbc.queryForObject("SELECT feature FROM \"tenant_" + org.slug() + "\".ai_usage_events",
                String.class)).isEqualTo("CHAT");
        assertThat(jdbc.queryForObject("SELECT project_id::text FROM \"tenant_" + org.slug() + "\".ai_usage_events",
                String.class)).isEqualTo(org.projectId().toString());
    }

    @Test
    @DisplayName("warnings go out once per level, the allowance stops AI generation but not reading, and a new period starts clean")
    void warnings_enforcement_and_rollover() {
        Org org = createOrg();
        String chat = "/api/projects/" + org.projectId() + "/assistant/messages";
        String topic = "organizations/" + org.id() + "/usage";
        String bearer = TestJwtFactory.bearer(OWNER_USER_ID, org.id().toString(), "ROLE_USER");

        record(org, usage(79_999, "a"));
        assertThat(warningLevel(org)).isEqualTo("NONE");
        verify(notifier, times(0)).broadcast(eq(topic), any());
        assertThat(guard(org)).isTrue();

        record(org, usage(1, "b"));
        record(org, usage(500, "c"));
        assertThat(warningLevel(org)).isEqualTo("WARNING_80");
        verify(notifier, times(1)).broadcast(eq(topic), any());

        record(org, usage(10_000, "d"));
        assertThat(warningLevel(org)).isEqualTo("WARNING_90");
        verify(notifier, times(2)).broadcast(eq(topic), any());

        // The chat still answers while there is room.
        assertThat(send(bearer, chat, "Quiero iniciar sesión con Google").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        record(org, usage(9_500, "e"));
        record(org, usage(5_000, "f"));
        assertThat(warningLevel(org)).isEqualTo("EXHAUSTED");
        ArgumentCaptor<Object> messages = ArgumentCaptor.forClass(Object.class);
        verify(notifier, times(3)).broadcast(eq(topic), messages.capture());
        assertThat(JSON.valueToTree(messages.getAllValues().getLast()).path("level").asString()).isEqualTo("EXHAUSTED");
        assertThat(JSON.valueToTree(messages.getAllValues().getFirst()).path("type").asString())
                .isEqualTo("TOKEN_QUOTA_WARNING");

        // AI generation is refused with the allowance spelled out; reading and the usage page are not.
        assertThat(guard(org)).isFalse();
        TenantContext.runWith(org.tenant(), () ->
                assertThatThrownBy(guard::assertAvailable).isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.error().code()).isEqualTo("AI_QUOTA_EXCEEDED");
                    assertThat(e.details()).containsEntry("plan", "FREE").containsEntry("limit", 100_000L);
                }));
        ResponseEntity<String> refused = send(bearer, chat, "Otro requisito");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        JsonNode problem = JSON.readTree(refused.getBody());
        assertThat(problem.path("code").asString()).isEqualTo("AI_QUOTA_EXCEEDED");
        assertThat(problem.path("plan").asString()).isEqualTo("FREE");
        assertThat(problem.path("limit").asLong()).isEqualTo(100_000L);
        assertThat(problem.path("used").asLong()).isGreaterThanOrEqualTo(100_000L);
        assertThat(get(bearer, chat).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(bearer, "/api/projects/" + org.projectId() + "/stories").getStatusCode()).isEqualTo(HttpStatus.OK);
        clearInvocations(notifier);

        // The period elapses: the allowance is available again at once, and the first call starts a fresh one.
        jdbc.update("UPDATE public.subscriptions SET current_period_end = now() - interval '1 minute' WHERE organization_id = ?",
                org.id());
        assertThat(guard(org)).isTrue();
        assertThat(usagePage(org).path("tokensUsed").asLong()).isZero();
        record(org, usage(123, "g"));
        assertThat(counter(org)).isEqualTo(123);
        assertThat(jdbc.queryForObject("SELECT quota_warning_percent FROM public.subscriptions WHERE organization_id = ?",
                Integer.class, org.id())).isZero();
        verify(notifier, times(0)).broadcast(eq(topic), any());
        assertThat(send(bearer, chat, "Un requisito nuevo").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("the usage page breaks the period down by feature and project, with a meeting-minutes estimate")
    void usage_page_breakdown() {
        Org org = createOrg();
        UUID other = UUID.randomUUID();
        record(org, TokenUsage.of(AiFeature.LIVE_SUGGESTIONS, org.projectId(), "m", 6_000, 600, 0, "u1"));
        record(org, TokenUsage.of(AiFeature.LIVE_SUGGESTIONS, other, "m", 3_000, 300, 0, "u2"));
        record(org, TokenUsage.of(AiFeature.CHAT, org.projectId(), "m", 800, 100, 0, "u3"));
        record(org, TokenUsage.of(AiFeature.EMBEDDINGS, org.projectId(), "e", 1_000, 0, 0, "u4"));

        JsonNode before = usagePage(org);
        assertThat(before.path("tokensUsed").asLong()).isEqualTo(6_600 + 3_300 + 900 + 100);
        JsonNode features = before.path("breakdown").path("byFeature");
        assertThat(features.get(0).path("feature").asString()).isEqualTo("LIVE_SUGGESTIONS");
        assertThat(features.get(0).path("tokens").asLong()).isEqualTo(9_900);
        assertThat(features.get(0).path("inputTokens").asLong()).isEqualTo(9_000);
        assertThat(features.get(0).path("calls").asLong()).isEqualTo(2);
        JsonNode projects = before.path("breakdown").path("topProjects");
        assertThat(projects.get(0).path("projectId").asString()).isEqualTo(org.projectId().toString());
        assertThat(projects.get(0).path("tokens").asLong()).isEqualTo(6_600 + 900 + 100);
        assertThat(before.path("meetingEstimate").path("measured").asBoolean()).isFalse();
        assertThat(before.path("meetingEstimate").path("tokensPerMinute").asLong()).isPositive();

        // 600 s of transcribed audio give the organization a measured rate: 9,900 live tokens over 10 minutes.
        record(org, TokenUsage.audio(null, "stt", 600));
        JsonNode measured = usagePage(org).path("meetingEstimate");
        assertThat(measured.path("measured").asBoolean()).isTrue();
        assertThat(measured.path("tokensPerMinute").asLong()).isEqualTo(990);
        assertThat(usagePage(org).path("breakdown").path("transcriptionSeconds").asLong()).isEqualTo(600);
        assertThat(usagePage(org).path("tokensUsed").asLong()).isEqualTo(6_600 + 3_300 + 900 + 100);
    }

    // ----- helpers -----

    private static TokenUsage usage(long tokens, String key) {
        return TokenUsage.of(AiFeature.LIVE_SUGGESTIONS, null, "m", tokens, 0, 0, key + "-" + UUID.randomUUID());
    }

    private void record(Org org, TokenUsage usage) {
        TenantContext.runWith(org.tenant(), () -> billing.recordAiUsage(org.id(), usage));
    }

    private boolean guard(Org org) {
        boolean[] available = new boolean[1];
        TenantContext.runWith(org.tenant(), () -> available[0] = guard.isAvailable());
        return available[0];
    }

    private long counter(Org org) {
        return jdbc.queryForObject("SELECT token_quota_used FROM public.subscriptions WHERE organization_id = ?",
                Long.class, org.id());
    }

    private long ledgerSum(Org org, String column) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM \"tenant_" + org.slug() + "\".ai_usage_events",
                Long.class);
    }

    private long ledgerCount(Org org) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM \"tenant_" + org.slug() + "\".ai_usage_events", Long.class);
    }

    private String warningLevel(Org org) {
        return usagePage(org).path("warningLevel").asString();
    }

    private JsonNode usagePage(Org org) {
        String bearer = TestJwtFactory.bearer(OWNER_USER_ID, org.id().toString(), "ROLE_USER");
        ResponseEntity<String> res = get(bearer, "/api/subscriptions/organization/" + org.id() + "/usage");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(res.getBody());
    }

    private ResponseEntity<String> get(String bearer, String uri) {
        return client().get().uri(uri)
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> send(String bearer, String uri, String content) {
        return client().post().uri(uri)
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private Org createOrg() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "meter-" + suffix;
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Meter " + suffix))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orgId = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM public.organizations WHERE slug = ?",
                String.class, slug));
        String bearer = TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER");
        ResponseEntity<String> project = client().post().uri("/api/organizations/" + orgId + "/projects")
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Metered", "programmingLanguages", List.of("Java"), "frameworks", List.of("Spring Boot"),
                        "clientPlatforms", List.of("Web"), "databases", List.of("PostgreSQL"),
                        "architecture", "Hexagonal", "domain", "Gastronomía"))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(project.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID projectId = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM \"tenant_" + slug
                + "\".projects WHERE organization_id = ?::uuid AND name = ?", String.class, orgId.toString(), "Metered"));
        return new Org(orgId, slug, projectId);
    }
}
