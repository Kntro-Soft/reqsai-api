package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.discovery.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the manual create-user-story slice across the full multitenant flow: it creates an
 * organization (provisions its tenant schema, including {@code user_stories} + the pgvector
 * {@code embedding} column), then — with a JWT carrying that {@code orgId} — creates stories and
 * verifies persistence and embedding-based duplicate detection in the tenant's schema.
 * <p>
 * A deterministic {@link EmbeddingPort} stub stands in for Ollama/Gemini so the dedup path is exercised
 * with real pgvector (the {@code <=>} operator) but without any external AI service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(CreateUserStoryIntegrationTest.StubEmbeddingConfig.class)
@Tag("integration")
@DisplayName("Integration: Create User Story")
class CreateUserStoryIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final Map<String, String> STORY = Map.of(
            "title", "Bulk import", "role", "analyst",
            "action", "upload a CSV", "benefit", "save time", "priority", "HIGH");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create the story in the caller's tenant schema (with an embedding)")
    void should_create_story_in_tenant_schema() {
        // Arrange — create an org (provisions its tenant schema + user_stories table)
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String orgId = createOrg(suffix, slug);
        UUID projectId = UUID.randomUUID();

        // Act — create a story as a member of that org (token carries orgId → routes to its schema)
        ResponseEntity<String> res = postStory(orgId, projectId, STORY);

        // Assert — HTTP
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).contains("\"status\":\"DRAFT\"");
        assertThat(res.getBody()).contains("\"priority\":\"HIGH\"");
        assertThat(res.getBody()).contains("\"projectId\":\"" + projectId + "\"");

        // Assert — row persisted in the tenant schema (not public), with its embedding populated
        Integer embedded = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"tenant_" + slug + "\".user_stories "
                        + "WHERE project_id = ?::uuid AND embedding IS NOT NULL",
                Integer.class, projectId.toString());
        assertThat(embedded).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject a near-duplicate story in the same project (409)")
    void should_reject_duplicate_story() {
        // Arrange — org + tenant + an existing story
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix, "acme-" + suffix);
        UUID projectId = UUID.randomUUID();
        assertThat(postStory(orgId, projectId, STORY).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Act — the same story again ⇒ identical embedding ⇒ cosine 1.0 ≥ threshold
        ResponseEntity<String> duplicate = postStory(orgId, projectId, STORY);

        // Assert
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("DUPLICATE_USER_STORY");
    }

    @Test
    @DisplayName("should reject an unauthenticated request")
    void should_reject_unauthenticated_request() {
        ResponseEntity<String> res = client().post().uri("/api/projects/{p}/stories", UUID.randomUUID())
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "x", "role", "a", "action", "b", "benefit", "c", "priority", "LOW"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private String createOrg(String suffix, String expectedSlug) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
    }

    private ResponseEntity<String> postStory(String orgId, UUID projectId, Map<String, String> body) {
        return client().post().uri("/api/projects/{p}/stories", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    /**
     * Deterministic stand-in for the Spring AI embedding model: identical text yields an identical
     * vector (cosine 1.0 ⇒ duplicate), different text yields a near-orthogonal vector (cosine ≈ 0 ⇒
     * distinct). Marked {@code @Primary} so it overrides {@code SpringAiEmbeddingPort} in tests.
     */
    @TestConfiguration
    static class StubEmbeddingConfig {

        @Bean
        @Primary
        EmbeddingPort stubEmbeddingPort() {
            return new EmbeddingPort() {
                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public float[] embed(String text) {
                    Random rnd = new Random(text.hashCode());
                    float[] vector = new float[UserStory.EMBEDDING_DIMENSIONS];
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] = (float) rnd.nextGaussian();
                    }
                    return vector;
                }
            };
        }
    }
}
