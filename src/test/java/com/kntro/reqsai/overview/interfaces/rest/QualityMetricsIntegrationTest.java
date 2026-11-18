package com.kntro.reqsai.overview.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quality indicators of a project end to end over HTTP and the tenant schema (US71): the stories and meetings
 * are seeded straight into the tenant tables, the indicators are computed from them, and the endpoint follows the
 * caller's project permissions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: quality indicators of a project")
class QualityMetricsIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String STORY_ONLY_USER_ID = "00000000-0000-0000-0000-000000000007";
    private static final String OUTSIDER_USER_ID = "00000000-0000-0000-0000-000000000008";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String NARRATIVE = "Reservar mesa\ncomensal\nreservar una mesa\nno hacer fila";
    private static final String OTHER_STORY = "Gestionar inventario de cocina\nchef\ncontrolar el stock de ingredientes\nevitar mermas";

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int serverPort;

    @Test
    @DisplayName("Given a project with accepted and rejected stories in the period, the lead sees the percentage accepted without major rewriting and the post-processing time per session")
    void happyPath() throws Exception {
        Fixture f = fixture();
        Instant now = Instant.now();
        String s = f.schema() + ".";

        // Discovery meeting, three days ago: one story kept, one rewritten, one rejected; review ends 3 h after it.
        Instant first = now.minus(3, ChronoUnit.DAYS);
        UUID discovery = session(s, f.project, "Kickoff", "DISCOVERY", first);
        story(s, f.project, discovery, "APPROVED", NARRATIVE, first.plus(1, ChronoUnit.HOURS));
        story(s, f.project, discovery, "APPROVED", OTHER_STORY, first.plus(3, ChronoUnit.HOURS));
        story(s, f.project, discovery, "REJECTED", NARRATIVE, first.plus(2, ChronoUnit.HOURS));

        // Clarification meeting, two days ago: its only story kept, review ends 30 minutes after it.
        Instant second = now.minus(2, ChronoUnit.DAYS);
        UUID clarification = session(s, f.project, "Aclaración", "CLARIFICATION", second);
        story(s, f.project, clarification, "APPROVED", NARRATIVE, second.plus(30, ChronoUnit.MINUTES));

        // Yesterday's meeting still has a story in draft: it is held, but its review is not finished.
        UUID pending = session(s, f.project, "Pendiente", "DISCOVERY", now.minus(1, ChronoUnit.DAYS));
        story(s, f.project, pending, "DRAFT", NARRATIVE, null);

        // A manual story decided in the period has nothing to compare with; an old meeting is outside the period.
        story(s, f.project, null, "APPROVED", null, now.minus(1, ChronoUnit.DAYS));
        UUID old = session(s, f.project, "Antigua", "DISCOVERY", now.minus(60, ChronoUnit.DAYS));
        story(s, f.project, old, "APPROVED", NARRATIVE, now.minus(60, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

        JsonNode body = body(get(OWNER_USER_ID, f.orgId, qualityUri(f.project, 9)));

        assertThat(body.path("hasData").asBoolean()).isTrue();
        assertThat(body.path("sessions").asLong()).isEqualTo(3);

        JsonNode acceptance = body.path("acceptance");
        assertThat(acceptance.path("decided").asLong()).isEqualTo(4);
        assertThat(acceptance.path("accepted").asLong()).isEqualTo(3);
        assertThat(acceptance.path("acceptedAsGenerated").asLong()).isEqualTo(2);
        assertThat(acceptance.path("acceptedRewritten").asLong()).isEqualTo(1);
        assertThat(acceptance.path("rejected").asLong()).isEqualTo(1);
        assertThat(acceptance.path("withoutBaseline").asLong()).isEqualTo(1);
        assertThat(acceptance.path("similarityThreshold").asDouble()).isEqualTo(0.8);
        assertThat(acceptance.path("rate").asDouble()).isEqualTo(0.5);

        JsonNode post = body.path("postProcessing");
        assertThat(post.path("measured").asLong()).isEqualTo(2);
        assertThat(post.path("unmeasured").asLong()).isEqualTo(1);
        assertThat(post.path("averageSeconds").asLong()).isEqualTo((10_800 + 1_800) / 2);
        assertThat(post.path("medianSeconds").asLong()).isEqualTo((10_800 + 1_800) / 2);
        JsonNode perSession = post.path("sessions");
        assertThat(perSession).hasSize(2);
        assertThat(perSession.get(0).path("sessionId").asString()).isEqualTo(clarification.toString());
        assertThat(perSession.get(0).path("purpose").asString()).isEqualTo("CLARIFICATION");
        assertThat(perSession.get(0).path("seconds").asLong()).isEqualTo(1_800);
        assertThat(perSession.get(1).path("sessionId").asString()).isEqualTo(discovery.toString());
        assertThat(perSession.get(1).path("seconds").asLong()).isEqualTo(10_800);

        JsonNode clarifications = body.path("clarification");
        assertThat(clarifications.path("meetings").asLong()).isEqualTo(1);
        assertThat(clarifications.path("sessions").asLong()).isEqualTo(3);
        assertThat(clarifications.path("share").asDouble()).isEqualTo(1.0 / 3.0);

        // The wider period brings in the old meeting and its decision.
        JsonNode wide = body(get(OWNER_USER_ID, f.orgId, qualityUri(f.project, 90)));
        assertThat(wide.path("sessions").asLong()).isEqualTo(4);
        assertThat(wide.path("acceptance").path("decided").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("Given a period without sessions, the lead sees the no-data state and no invented figure")
    void periodWithoutSessions() throws Exception {
        Fixture f = fixture();
        // The only activity is two months old: nothing in the last ten days.
        Instant long_ago = Instant.now().minus(60, ChronoUnit.DAYS);
        UUID old = session(f.schema() + ".", f.project, "Antigua", "DISCOVERY", long_ago);
        story(f.schema() + ".", f.project, old, "APPROVED", NARRATIVE, long_ago.plus(1, ChronoUnit.HOURS));

        JsonNode body = body(get(OWNER_USER_ID, f.orgId, qualityUri(f.project, 9)));

        assertThat(body.path("hasData").asBoolean()).isFalse();
        assertThat(body.path("sessions").asLong()).isZero();
        assertThat(body.path("acceptance").path("decided").asLong()).isZero();
        assertThat(body.path("acceptance").path("rate").isNull()).isTrue();
        assertThat(body.path("postProcessing").path("averageSeconds").isNull()).isTrue();
        assertThat(body.path("postProcessing").path("medianSeconds").isNull()).isTrue();
        assertThat(body.path("postProcessing").path("sessions")).isEmpty();
        assertThat(body.path("clarification").path("meetings").asLong()).isZero();
        assertThat(body.path("clarification").path("share").isNull()).isTrue();
    }

    @Test
    @DisplayName("without dates the period is the last 30 days up to today")
    void defaultPeriod() throws Exception {
        Fixture f = fixture();
        session(f.schema() + ".", f.project, "Hace veinte días", "DISCOVERY", Instant.now().minus(20, ChronoUnit.DAYS));
        session(f.schema() + ".", f.project, "Hace cuarenta días", "DISCOVERY", Instant.now().minus(40, ChronoUnit.DAYS));

        JsonNode body = body(get(OWNER_USER_ID, f.orgId, "/api/projects/" + f.project + "/quality-metrics"));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(body.path("to").asString()).isEqualTo(today.toString());
        assertThat(body.path("from").asString()).isEqualTo(today.minusDays(29).toString());
        assertThat(body.path("sessions").asLong()).isEqualTo(1);
        assertThat(body.path("hasData").asBoolean()).isTrue();
        assertThat(body.path("acceptance").path("rate").isNull()).isTrue();
    }

    @Test
    @DisplayName("a session is created as a discovery meeting unless it says it is a clarification")
    void sessionPurposeOverHttp() throws Exception {
        Fixture f = fixture();

        ResponseEntity<String> plain = post(OWNER_USER_ID, f.orgId, "/api/projects/" + f.project + "/sessions",
                Map.of("title", "Kickoff", "language", "es-PE"));
        ResponseEntity<String> followUp = post(OWNER_USER_ID, f.orgId, "/api/projects/" + f.project + "/sessions",
                Map.of("title", "Aclaración", "language", "es-PE", "purpose", "CLARIFICATION"));
        ResponseEntity<String> invalid = post(OWNER_USER_ID, f.orgId, "/api/projects/" + f.project + "/sessions",
                Map.of("title", "Raro", "language", "es-PE", "purpose", "WORKSHOP"));

        assertThat(plain.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(JSON.readTree(plain.getBody()).path("purpose").asString()).isEqualTo("DISCOVERY");
        assertThat(followUp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = JSON.readTree(followUp.getBody());
        assertThat(created.path("purpose").asString()).isEqualTo("CLARIFICATION");
        assertThat(jdbc.queryForObject("SELECT purpose FROM " + f.schema() + ".discovery_sessions WHERE id = ?::uuid",
                String.class, created.path("id").asString())).isEqualTo("CLARIFICATION");
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("approving and rejecting a story over HTTP records when the decision was taken, and a draft has none")
    void decisionTimeOverHttp() {
        Fixture f = fixture();
        UUID story = story(f.schema() + ".", f.project, null, "DRAFT", NARRATIVE, null);
        String uri = "/api/projects/" + f.project + "/stories/" + story + "/status";
        Instant before = Instant.now().minusSeconds(1);

        assertThat(patch(OWNER_USER_ID, f.orgId, uri, Map.of("status", "APPROVED")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        Timestamp approvedAt = reviewedAt(f, story);
        assertThat(approvedAt).isNotNull();
        assertThat(approvedAt.toInstant()).isAfter(before);

        assertThat(patch(OWNER_USER_ID, f.orgId, uri, Map.of("status", "DRAFT")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(reviewedAt(f, story)).isNull();
    }

    @Test
    @DisplayName("needs STORY_READ and SESSION_READ on the project; others are turned away")
    void permissions() {
        Fixture f = fixture();
        String uri = qualityUri(f.project, 9);

        assertThat(get(STORY_ONLY_USER_ID, f.orgId, uri).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> outsider = get(OUTSIDER_USER_ID, f.orgId, uri);
        assertThat(outsider.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(outsider.getBody()).contains("QUALITY_ACCESS_DENIED");
        assertThat(get(OWNER_USER_ID, f.orgId, qualityUri(UUID.randomUUID(), 9)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rawGet(uri, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("an inverted or oversized period is a bad request")
    void invalidPeriod() {
        Fixture f = fixture();
        String base = "/api/projects/" + f.project + "/quality-metrics";

        ResponseEntity<String> inverted = get(OWNER_USER_ID, f.orgId, base + "?from=2026-11-10&to=2026-11-01");
        assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inverted.getBody()).contains("QUALITY_INVALID_PERIOD");
        assertThat(get(OWNER_USER_ID, f.orgId, base + "?from=2020-01-01&to=2026-11-01").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(OWNER_USER_ID, f.orgId, base + "?from=ayer").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private record Fixture(UUID orgId, String slug, UUID project) {
        String schema() {
            return "\"tenant_" + slug + "\"";
        }
    }

    /** An organization with an owner, a member who can read stories only, an unassigned outsider and one project. */
    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        ResponseEntity<String> created = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1").contentType(MediaType.APPLICATION_JSON).body(Map.of("name", "Acme " + suffix))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orgId = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM public.organizations WHERE slug = ?",
                String.class, slug));
        jdbc.update("UPDATE public.organizations SET member_base_permission = 'NONE' WHERE id = ?", orgId);
        createMember(orgId, STORY_ONLY_USER_ID, "reader@example.com");
        createMember(orgId, OUTSIDER_USER_ID, "outsider@example.com");

        Fixture base = new Fixture(orgId, slug, null);
        UUID project = createProject(base, "Alpha");
        Fixture f = new Fixture(orgId, slug, project);

        ResponseEntity<String> role = post(OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/projects/" + project + "/roles",
                Map.of("name", "Solo historias", "permissions", List.of("STORY_READ")));
        assertThat(role.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String roleId = jdbc.queryForObject("SELECT id::text FROM " + f.schema() + ".project_roles WHERE project_id = ?::uuid"
                + " AND name = ?", String.class, project, "Solo historias");
        UUID member = UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?", String.class,
                orgId.toString(), "reader@example.com"));
        assertThat(post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects/" + project + "/members",
                Map.of("memberId", member.toString(), "roleId", roleId)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return f;
    }

    private static String qualityUri(UUID project, int daysBack) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "/api/projects/" + project + "/quality-metrics?from=" + today.minusDays(daysBack) + "&to=" + today;
    }

    /** A finished meeting: it ended at {@code endedAt}. */
    private UUID session(String s, UUID project, String title, String purpose, Instant endedAt) {
        UUID id = UUID.randomUUID();
        Timestamp ended = Timestamp.from(endedAt);
        jdbc.update("INSERT INTO " + s + "discovery_sessions (id, project_id, title, language, status, purpose, ended_at,"
                + " created_at, updated_at) VALUES (?, ?, ?, 'es-PE', 'COMPLETED', ?, ?, ?, ?)", id, project, title,
                purpose, ended, ended, ended);
        return id;
    }

    /**
     * A story that currently says {@link #NARRATIVE}; {@code generatedText} is what the AI proposed (null for a story
     * that did not come from the AI) and {@code reviewedAt} when it was decided (null for a draft).
     */
    private UUID story(String s, UUID project, UUID session, String status, String generatedText, Instant reviewedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + s + "user_stories (id, project_id, session_id, title, role, action, benefit, priority,"
                + " status, generated_text, reviewed_at, created_at, updated_at)"
                + " VALUES (?, ?, ?, 'Reservar mesa', 'comensal', 'reservar una mesa', 'no hacer fila', 'MEDIUM', ?, ?, ?,"
                + " now(), now())", id, project, session, status, generatedText,
                reviewedAt == null ? null : Timestamp.from(reviewedAt));
        return id;
    }

    private Timestamp reviewedAt(Fixture f, UUID story) {
        return jdbc.queryForObject("SELECT reviewed_at FROM " + f.schema() + ".user_stories WHERE id = ?", Timestamp.class,
                story);
    }

    private static JsonNode body(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(response.getBody());
    }

    private void createMember(UUID orgId, String userId, String email) {
        ResponseEntity<String> res = post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members",
                Map.of("userId", userId, "email", email, "displayName", email, "role", "MEMBER"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private UUID createProject(Fixture f, String name) {
        ResponseEntity<String> res = post(OWNER_USER_ID, f.orgId, "/api/organizations/" + f.orgId + "/projects",
                Map.of("name", name, "description", "Proyecto", "programmingLanguages", List.of("Java"), "frameworks",
                        List.of("Spring Boot"), "clientPlatforms", List.of("Web"), "databases", List.of("PostgreSQL"),
                        "architecture", "Clean Architecture", "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM " + f.schema() + ".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, f.orgId.toString(), name));
    }

    private ResponseEntity<String> get(String userId, UUID orgId, String uri) {
        return rawGet(uri, TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"));
    }

    private ResponseEntity<String> rawGet(String uri, String authorization) {
        var spec = client().get().uri(uri).header("Api-Version", "1");
        if (authorization != null) {
            spec = spec.header("Authorization", authorization);
        }
        return spec.exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> post(String userId, UUID orgId, String uri, Map<String, Object> body) {
        return client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1").contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> patch(String userId, UUID orgId, String uri, Map<String, Object> body) {
        // The shared client() cannot send PATCH (HttpURLConnection); the JDK HttpClient factory can.
        return RestClient.builder().baseUrl("http://localhost:" + serverPort)
                .requestFactory(new JdkClientHttpRequestFactory()).build().patch().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1").contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }
}
