package com.kntro.reqsai.discovery.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubEmbeddingConfig;
import com.kntro.reqsai.testsupport.TestJwtFactory;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end (US68): evaluating and reading the quality of a story over HTTP, through tenant routing, with the
 * assessment stored in the tenant schema. The evaluation is rule-based, so no AI provider is involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubEmbeddingConfig.class)
@Tag("integration")
@DisplayName("Integration: Story quality score")
class StoryQualityIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final Map<String, Object> GOOD_STORY = Map.of(
            "title", "Exportar el backlog", "role", "analista de cumplimiento",
            "action", "exportar el backlog a CSV", "benefit", "compartirlo con el cliente sin reescribirlo",
            "priority", "MEDIUM", "storyPoints", 3);
    private static final Map<String, Object> VAGUE_STORY = Map.of(
            "title", "Exportar", "role", "usuario",
            "action", "exportar el backlog de forma rápida y fácil", "benefit", "mejor control, etc.",
            "priority", "MEDIUM");
    private static final Map<String, String> GOOD_CRITERION = Map.of(
            "scenario", "camino feliz",
            "given", "hay historias aprobadas en el backlog",
            "when", "el analista exporta el backlog",
            "then", "el sistema descarga un archivo CSV con todas las historias aprobadas");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Tenant(String orgId, String slug, UUID projectId) {
    }

    @Test
    @DisplayName("Given a generated story with acceptance criteria, When the analyst evaluates it, Then it shows a score from 0 to 100 and each characteristic with its observation")
    void happy_path_score_and_characteristics() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, GOOD_STORY);
        addCriterion(tenant, storyId, GOOD_CRITERION);

        ResponseEntity<String> res = evaluate(tenant, storyId);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(res.getBody());
        assertThat(body.get("storyId").asText()).isEqualTo(storyId);
        assertThat(body.get("score").asInt()).isEqualTo(100);
        assertThat(body.get("evaluatedAt").asText()).isNotBlank();
        JsonNode characteristics = body.get("characteristics");
        assertThat(characteristics).hasSize(4);
        for (JsonNode characteristic : characteristics) {
            assertThat(characteristic.get("met").asBoolean()).isTrue();
            assertThat(characteristic.get("score").asInt()).isEqualTo(100);
            assertThat(characteristic.get("observation").asText()).isNotBlank();
            assertThat(characteristic.get("suggestion").isNull()).isTrue();
        }
        assertThat(characteristics.findValuesAsText("characteristic"))
                .containsExactly("AMBIGUITY", "ATOMICITY", "VERIFIABILITY", "COMPLETENESS");
        assertThat(storedAssessments(tenant, storyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Given a story with ambiguous terms and no acceptance criteria, When the system evaluates it, Then it flags the unmet characteristics and suggests how to fix them")
    void unhappy_path_flags_unmet_characteristics() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, VAGUE_STORY);

        ResponseEntity<String> res = evaluate(tenant, storyId);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(res.getBody());
        assertThat(body.get("score").asInt()).isLessThan(50);
        JsonNode ambiguity = characteristic(body, "AMBIGUITY");
        assertThat(ambiguity.get("met").asBoolean()).isFalse();
        assertThat(ambiguity.get("observation").asText()).contains("«rápido»").contains("«fácil»").contains("«etc.»");
        assertThat(ambiguity.get("suggestion").asText()).isNotBlank();
        JsonNode verifiability = characteristic(body, "VERIFIABILITY");
        assertThat(verifiability.get("met").asBoolean()).isFalse();
        assertThat(verifiability.get("score").asInt()).isZero();
        assertThat(verifiability.get("suggestion").asText()).contains("Dado/Cuando/Entonces");
        JsonNode completeness = characteristic(body, "COMPLETENESS");
        assertThat(completeness.get("met").asBoolean()).isFalse();
        assertThat(completeness.get("observation").asText()).contains("genérico").contains("criterios de aceptación");
        assertThat(characteristic(body, "ATOMICITY").get("met").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("GET returns the stored assessment without evaluating again")
    void get_returns_the_latest_assessment() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, VAGUE_STORY);
        JsonNode evaluated = objectMapper.readTree(evaluate(tenant, storyId).getBody());

        ResponseEntity<String> res = read(tenant, storyId);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(res.getBody())).isEqualTo(evaluated);
    }

    @Test
    @DisplayName("GET on a story that was never evaluated answers 404 STORY_QUALITY_NOT_EVALUATED")
    void get_before_evaluating_is_404() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, GOOD_STORY);

        ResponseEntity<String> res = read(tenant, storyId);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(objectMapper.readTree(res.getBody()).get("code").asText()).isEqualTo("STORY_QUALITY_NOT_EVALUATED");
    }

    @Test
    @DisplayName("evaluating again re-evaluates and replaces the assessment (one row per story)")
    void reevaluating_replaces_the_assessment() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, GOOD_STORY);
        JsonNode first = objectMapper.readTree(evaluate(tenant, storyId).getBody());
        assertThat(first.get("score").asInt()).isLessThan(100);

        addCriterionWithoutRefresh(tenant, storyId);
        JsonNode second = objectMapper.readTree(evaluate(tenant, storyId).getBody());

        assertThat(second.get("score").asInt()).isEqualTo(100);
        assertThat(storedAssessments(tenant, storyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("editing an already-assessed story or its criteria refreshes the assessment on its own")
    void editing_refreshes_an_existing_assessment() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, GOOD_STORY);
        assertThat(objectMapper.readTree(evaluate(tenant, storyId).getBody()).get("score").asInt()).isLessThan(100);

        addCriterion(tenant, storyId, GOOD_CRITERION);
        assertThat(objectMapper.readTree(read(tenant, storyId).getBody()).get("score").asInt()).isEqualTo(100);

        Map<String, Object> vague = new HashMap<>(GOOD_STORY);
        vague.put("action", "exportar el backlog de forma rápida");
        ResponseEntity<String> updated = client().put().uri("/api/projects/{p}/stories/{s}", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(vague)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode afterEdit = objectMapper.readTree(read(tenant, storyId).getBody());
        assertThat(characteristic(afterEdit, "AMBIGUITY").get("met").asBoolean()).isFalse();
        assertThat(afterEdit.get("score").asInt()).isLessThan(100);
        assertThat(storedAssessments(tenant, storyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("editing a story that was never evaluated does not create an assessment")
    void editing_does_not_start_evaluating() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, GOOD_STORY);

        addCriterion(tenant, storyId, GOOD_CRITERION);

        assertThat(storedAssessments(tenant, storyId)).isZero();
    }

    @Test
    @DisplayName("deleting the story removes its assessment")
    void deleting_the_story_removes_the_assessment() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, GOOD_STORY);
        evaluate(tenant, storyId);
        assertThat(storedAssessments(tenant, storyId)).isEqualTo(1);

        ResponseEntity<Void> deleted = client().delete().uri("/api/projects/{p}/stories/{s}", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).build(), false);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(storedAssessments(tenant, storyId)).isZero();
    }

    @Test
    @DisplayName("a story that is not in the project answers 404 on both endpoints")
    void story_outside_the_project_is_404() throws Exception {
        Tenant tenant = newTenant();
        String storyId = createStory(tenant, GOOD_STORY);
        Tenant otherProject = new Tenant(tenant.orgId(), tenant.slug(), UUID.randomUUID());

        assertThat(evaluate(otherProject, storyId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(read(otherProject, storyId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(evaluate(tenant, UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("requires authentication")
    void requires_authentication() {
        ResponseEntity<String> post = client().post()
                .uri("/api/projects/{p}/stories/{s}/quality", UUID.randomUUID(), UUID.randomUUID())
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).build(), false);
        ResponseEntity<String> get = client().get()
                .uri("/api/projects/{p}/stories/{s}/quality", UUID.randomUUID(), UUID.randomUUID())
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).build(), false);

        assertThat(post.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        assertThat(get.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static JsonNode characteristic(JsonNode body, String name) {
        for (JsonNode characteristic : body.get("characteristics")) {
            if (characteristic.get("characteristic").asText().equals(name)) {
                return characteristic;
            }
        }
        throw new AssertionError("no finding for " + name);
    }

    private int storedAssessments(Tenant tenant, String storyId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"tenant_" + tenant.slug() + "\".story_quality_assessments WHERE story_id = ?::uuid",
                Integer.class, storyId);
        return count == null ? 0 : count;
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

    /** Adds the criterion straight to the tenant table, so the stored assessment stays as it was. */
    private void addCriterionWithoutRefresh(Tenant tenant, String storyId) {
        jdbcTemplate.update("INSERT INTO \"tenant_" + tenant.slug() + "\".acceptance_criteria "
                        + "(id, story_id, scenario, given_text, when_text, then_text) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), storyId, GOOD_CRITERION.get("scenario"), GOOD_CRITERION.get("given"),
                GOOD_CRITERION.get("when"), GOOD_CRITERION.get("then"));
    }

    private ResponseEntity<String> evaluate(Tenant tenant, String storyId) {
        return client().post().uri("/api/projects/{p}/stories/{s}/quality", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<String> read(Tenant tenant, String storyId) {
        return client().get().uri("/api/projects/{p}/stories/{s}/quality", tenant.projectId(), storyId)
                .header("Authorization", bearer(tenant))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)), false);
    }
}
