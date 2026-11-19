package com.kntro.reqsai.discovery.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubEmbeddingConfig;
import com.kntro.reqsai.testsupport.StubStoryImprovementConfig;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end (US73): proposing and applying an AI improvement of a story over HTTP, through tenant routing, with a
 * stub in place of the AI. Proposing stores nothing; applying replaces the text and the criteria in one
 * transaction, evaluates and stores the quality, keeps the status and never touches the generated text.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({StubEmbeddingConfig.class, StubStoryImprovementConfig.class})
@Tag("integration")
@DisplayName("Integration: Improve a story with AI")
class StoryImprovementIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String TOKEN = StubStoryImprovementConfig.TOKEN;
    private static final Map<String, Object> VAGUE_STORY = Map.of(
            "title", "Exportar", "role", "usuario",
            "action", "exportar el backlog de forma rápida y fácil", "benefit", "mejor control, etc.",
            "priority", "HIGH", "storyPoints", 5);
    private static final Map<String, String> OLD_CRITERION = Map.of(
            "scenario", "viejo", "given", "dado viejo", "when", "cuando viejo", "then", "entonces viejo");
    private static final String GENERATED_TEXT = "Exportar\nusuario\nexportar el backlog de forma rápida y fácil\n"
            + "mejor control, etc.";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Tenant(String orgId, String slug, UUID projectId) {
    }

    @BeforeEach
    void resetStub() {
        StubStoryImprovementConfig.reset();
    }

    @Test
    @DisplayName("Given a vague story, When the analyst asks for an improvement, Then it returns a proposal with pending values, fixes and the expected quality, and changes nothing")
    void propose_does_not_change_the_story() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, VAGUE_STORY);
        addCriterion(tenant, storyId, OLD_CRITERION);
        UUID session = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO \"tenant_" + tenant.slug() + "\".story_source_fragments "
                        + "(story_id, session_id, sequence, quote) VALUES (?::uuid, ?, 1, ?)",
                storyId, session, "necesito exportar el backlog rápido");
        JsonNode before = objectMapper.readTree(getStory(tenant, storyId).getBody());

        ResponseEntity<String> res = propose(tenant, storyId);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(res.getBody());
        assertThat(body.get("storyId").asText()).isEqualTo(storyId);
        JsonNode proposal = body.get("proposal");
        assertThat(proposal.get("title").asText()).isEqualTo("Exportar el backlog a CSV");
        assertThat(proposal.get("role").asText()).isEqualTo("analista de cumplimiento");
        assertThat(proposal.get("action").asText()).isEqualTo("exportar el backlog aprobado a un archivo CSV");
        assertThat(proposal.get("benefit").asText()).isEqualTo("compartirlo con el cliente sin reescribirlo");
        JsonNode criterion = proposal.get("acceptanceCriteria").get(0);
        assertThat(criterion.get("scenario").asText()).isEqualTo("Exportación correcta");
        assertThat(criterion.get("given").asText()).isNotBlank();
        assertThat(criterion.get("when").asText()).isNotBlank();
        assertThat(criterion.get("then").asText()).contains(TOKEN);
        assertThat(body.get("pendingValues")).hasSize(1);
        assertThat(body.get("pendingValues").get(0).get("token").asText()).isEqualTo(TOKEN);
        assertThat(body.get("pendingValues").get(0).get("hint").asText()).isEqualTo("¿En cuántos segundos debe responder?");
        assertThat(body.get("fixes").get(0).asText()).contains("«rápido»");
        JsonNode expected = body.get("expectedQuality");
        assertThat(expected.get("storyId").asText()).isEqualTo(storyId);
        assertThat(expected.get("score").asInt()).isEqualTo(100);
        assertThat(expected.get("characteristics")).hasSize(4);
        assertThat(expected.get("evaluatedAt").asText()).isNotBlank();

        // What reached the model: the stored story, its unmet findings and the quote it comes from.
        var request = StubStoryImprovementConfig.LAST_REQUEST.get();
        assertThat(request.story().action()).isEqualTo("exportar el backlog de forma rápida y fácil");
        assertThat(request.findings()).extracting(f -> f.characteristic()).contains("AMBIGUITY");
        assertThat(request.sourceQuotes()).containsExactly("necesito exportar el backlog rápido");
        // Nothing was stored or changed.
        JsonNode after = objectMapper.readTree(getStory(tenant, storyId).getBody());
        assertThat(after).isEqualTo(before);
        assertThat(storedAssessments(tenant, storyId)).isZero();
        assertThat(criteriaCount(tenant, storyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Given a reviewed proposal, When the analyst applies it, Then the fields and criteria are replaced, the quality is re-evaluated and stored, and the status is kept")
    void apply_updates_the_story_and_stores_the_quality() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, VAGUE_STORY);
        addCriterion(tenant, storyId, OLD_CRITERION);
        addCriterion(tenant, storyId, Map.of("given", "otro dado", "when", "otro cuando", "then", "otro entonces"));
        jdbcTemplate.update("UPDATE \"tenant_" + tenant.slug() + "\".user_stories SET status = 'APPROVED', "
                + "reviewed_at = now() WHERE id = ?::uuid", storyId);

        ResponseEntity<String> res = apply(tenant, storyId, filledProposal());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(res.getBody());
        JsonNode story = body.get("story");
        assertThat(story.get("id").asText()).isEqualTo(storyId);
        assertThat(story.get("title").asText()).isEqualTo("Exportar el backlog a CSV");
        assertThat(story.get("role").asText()).isEqualTo("analista de cumplimiento");
        assertThat(story.get("action").asText()).isEqualTo("exportar el backlog aprobado a un archivo CSV");
        assertThat(story.get("benefit").asText()).isEqualTo("compartirlo con el cliente sin reescribirlo");
        assertThat(story.get("priority").asText()).isEqualTo("HIGH");
        assertThat(story.get("storyPoints").asInt()).isEqualTo(5);
        assertThat(story.get("status").asText()).isEqualTo("APPROVED");
        assertThat(story.get("acceptanceCriteria")).hasSize(1);
        assertThat(story.get("acceptanceCriteria").get(0).get("then").asText())
                .isEqualTo("el sistema descarga un archivo CSV en menos de 3 segundos");
        JsonNode quality = body.get("quality");
        assertThat(quality.get("storyId").asText()).isEqualTo(storyId);
        assertThat(quality.get("score").asInt()).isEqualTo(100);
        assertThat(quality.get("characteristics")).hasSize(4);

        // The criteria were replaced in the database and the assessment is the stored one.
        assertThat(criteriaCount(tenant, storyId)).isEqualTo(1);
        assertThat(storedAssessments(tenant, storyId)).isEqualTo(1);
        JsonNode stored = objectMapper.readTree(getQuality(tenant, storyId).getBody());
        assertThat(stored).isEqualTo(quality);
        JsonNode reread = objectMapper.readTree(getStory(tenant, storyId).getBody());
        assertThat(reread.get("title").asText()).isEqualTo("Exportar el backlog a CSV");
        assertThat(reread.get("status").asText()).isEqualTo("APPROVED");
        assertThat(reread.get("acceptanceCriteria")).hasSize(1);
    }

    @Test
    @DisplayName("Given a proposal with a pending value left, When it is applied, Then 422 IMPROVEMENT_PENDING_VALUES and the story is unchanged")
    void apply_with_a_leftover_token_is_422() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, VAGUE_STORY);
        addCriterion(tenant, storyId, OLD_CRITERION);
        JsonNode before = objectMapper.readTree(getStory(tenant, storyId).getBody());

        ResponseEntity<String> res = apply(tenant, storyId, proposalWithThen("el sistema responde en menos de " + TOKEN + " segundos"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        JsonNode problem = objectMapper.readTree(res.getBody());
        assertThat(problem.get("code").asText()).isEqualTo("IMPROVEMENT_PENDING_VALUES");
        assertThat(problem.get("detail").asText()).contains(TOKEN);
        assertThat(objectMapper.readTree(getStory(tenant, storyId).getBody())).isEqualTo(before);
        assertThat(storedAssessments(tenant, storyId)).isZero();
        assertThat(criteriaCount(tenant, storyId)).isEqualTo(1);

        // A token in the title is refused too.
        Map<String, Object> inTitle = new HashMap<>(filledProposal());
        inTitle.put("title", "Exportar a [[formato]]");
        ResponseEntity<String> titleRes = apply(tenant, storyId, inTitle);
        assertThat(titleRes.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(objectMapper.readTree(titleRes.getBody()).get("code").asText()).isEqualTo("IMPROVEMENT_PENDING_VALUES");
    }

    @Test
    @DisplayName("Applying does not touch generated_text, so the improvement counts as a rewrite in the quality indicators")
    void generated_text_is_untouched() {
        Tenant tenant = newTenant();
        String storyId = createStoryId(tenant);
        jdbcTemplate.update("UPDATE \"tenant_" + tenant.slug() + "\".user_stories SET generated_text = ? WHERE id = ?::uuid",
                GENERATED_TEXT, storyId);

        assertThat(propose(tenant, storyId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(generatedText(tenant, storyId)).isEqualTo(GENERATED_TEXT);
        assertThat(apply(tenant, storyId, filledProposal()).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(generatedText(tenant, storyId)).isEqualTo(GENERATED_TEXT);
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM \"tenant_" + tenant.slug()
                + "\".user_stories WHERE id = ?::uuid", String.class, storyId)).isEqualTo("Exportar el backlog a CSV");
    }

    @Test
    @DisplayName("a request that breaks the field limits is a 400 and changes nothing")
    void apply_validates_the_limits() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStoryId(tenant);

        Map<String, Object> longTitle = new HashMap<>(filledProposal());
        longTitle.put("title", "t".repeat(201));
        Map<String, Object> blankRole = new HashMap<>(filledProposal());
        blankRole.put("role", " ");
        Map<String, Object> noCriteria = new HashMap<>(filledProposal());
        noCriteria.remove("acceptanceCriteria");
        Map<String, Object> longThen = new HashMap<>(filledProposal());
        longThen.put("acceptanceCriteria", List.of(Map.of("given", "g", "when", "w", "then", "x".repeat(1001))));

        for (Map<String, Object> body : List.of(longTitle, blankRole, noCriteria, longThen)) {
            assertThat(apply(tenant, storyId, body).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(objectMapper.readTree(getStory(tenant, storyId).getBody()).get("title").asText()).isEqualTo("Exportar");
    }

    @Test
    @DisplayName("with the monthly AI allowance spent, proposing answers 402 AI_QUOTA_EXCEEDED without calling the AI, and applying still works")
    void over_the_allowance_is_402() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStoryId(tenant);
        jdbcTemplate.update("UPDATE public.subscriptions SET token_quota_used = 1000000000 WHERE organization_id = ?::uuid",
                tenant.orgId());

        ResponseEntity<String> res = propose(tenant, storyId);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(objectMapper.readTree(res.getBody()).get("code").asText()).isEqualTo("AI_QUOTA_EXCEEDED");
        assertThat(StubStoryImprovementConfig.CALLS.get()).isZero();
        assertThat(apply(tenant, storyId, filledProposal()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a story that is not in the project answers 404 on both endpoints")
    void story_outside_the_project_is_404() {
        Tenant tenant = newTenant();
        String storyId = createStoryId(tenant);
        Tenant otherProject = new Tenant(tenant.orgId(), tenant.slug(), UUID.randomUUID());

        assertThat(propose(otherProject, storyId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(apply(otherProject, storyId, filledProposal()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(propose(tenant, UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(apply(tenant, UUID.randomUUID().toString(), filledProposal()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(StubStoryImprovementConfig.CALLS.get()).isZero();
    }

    @Test
    @DisplayName("requires authentication")
    void requires_authentication() {
        ResponseEntity<String> propose = client().post()
                .uri("/api/projects/{p}/stories/{s}/quality/improvement", UUID.randomUUID(), UUID.randomUUID())
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).build(), false);
        ResponseEntity<String> apply = client().post()
                .uri("/api/projects/{p}/stories/{s}/quality/improvement/apply", UUID.randomUUID(), UUID.randomUUID())
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(filledProposal())
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).build(), false);

        assertThat(propose.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        assertThat(apply.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        assertThat(StubStoryImprovementConfig.CALLS.get()).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static Map<String, Object> proposalWithThen(String then) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Exportar el backlog a CSV");
        body.put("role", "analista de cumplimiento");
        body.put("action", "exportar el backlog aprobado a un archivo CSV");
        body.put("benefit", "compartirlo con el cliente sin reescribirlo");
        body.put("acceptanceCriteria", List.of(Map.of(
                "scenario", "Exportación correcta",
                "given", "hay historias aprobadas en el backlog",
                "when", "el analista exporta el backlog",
                "then", then)));
        return body;
    }

    /** The stub's proposal with its only token replaced by the analyst. */
    private static Map<String, Object> filledProposal() {
        return proposalWithThen("el sistema descarga un archivo CSV en menos de 3 segundos");
    }

    private int storedAssessments(Tenant tenant, String storyId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"tenant_" + tenant.slug() + "\".story_quality_assessments WHERE story_id = ?::uuid",
                Integer.class, storyId);
        return count == null ? 0 : count;
    }

    private int criteriaCount(Tenant tenant, String storyId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"tenant_" + tenant.slug() + "\".acceptance_criteria WHERE story_id = ?::uuid",
                Integer.class, storyId);
        return count == null ? 0 : count;
    }

    private String generatedText(Tenant tenant, String storyId) {
        return jdbcTemplate.queryForObject(
                "SELECT generated_text FROM \"tenant_" + tenant.slug() + "\".user_stories WHERE id = ?::uuid",
                String.class, storyId);
    }

    private Tenant newTenant() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orgId = jdbcTemplate.queryForObject("SELECT id FROM public.organizations WHERE slug = ?", String.class, slug);
        return new Tenant(orgId, slug, UUID.randomUUID());
    }

    private static String bearer(Tenant tenant) {
        return TestJwtFactory.bearer(USER_ID, tenant.orgId(), "ROLE_USER");
    }

    private String createStoryId(Tenant tenant) {
        try {
            return createStory(tenant, Map.of("title", "Exportar", "role", "usuario",
                    "action", "exportar el backlog de forma rápida", "benefit", "mejor control",
                    "priority", "MEDIUM"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String createStory(Tenant tenant, Map<String, Object> body) throws Exception {
        ResponseEntity<String> res = client().post().uri("/api/projects/{p}/stories", tenant.projectId())
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(res.getBody()).get("id").asText();
    }

    private void addCriterion(Tenant tenant, String storyId, Map<String, String> body) {
        ResponseEntity<String> res = client().post().uri("/api/projects/{p}/stories/{s}/criteria", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> getStory(Tenant tenant, String storyId) {
        return client().get().uri("/api/projects/{p}/stories/{s}", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<String> getQuality(Tenant tenant, String storyId) {
        return client().get().uri("/api/projects/{p}/stories/{s}/quality", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<String> propose(Tenant tenant, String storyId) {
        return client().post().uri("/api/projects/{p}/stories/{s}/quality/improvement", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<String> apply(Tenant tenant, String storyId, Map<String, Object> body) {
        return client().post().uri("/api/projects/{p}/stories/{s}/quality/improvement/apply", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
    }
}
