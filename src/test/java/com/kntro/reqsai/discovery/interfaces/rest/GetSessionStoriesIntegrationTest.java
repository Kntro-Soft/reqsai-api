package com.kntro.reqsai.discovery.interfaces.rest;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for GET /api/sessions/{sessionId}/stories and GET /{storyId}.
 * <p>
 * Stories with a {@code sessionId} are produced by the AI pipeline (not yet implemented as a REST
 * endpoint), so this test inserts rows directly into the tenant schema via {@link JdbcTemplate} to
 * cover the happy-path and session-isolation scenarios. The remaining cases (404, auth) are covered
 * without needing pre-existing stories.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubEmbeddingConfig.class)
@Tag("integration")
@DisplayName("Integration: Get Session Stories")
class GetSessionStoriesIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should return a story by id when it belongs to the session")
    void should_return_story_by_id() {
        // Arrange — create org + session, then seed a story with that sessionId directly
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        String schemaName = "tenant_acme-" + suffix;
        UUID projectId = UUID.randomUUID();
        UUID sessionId = createSession(orgId, projectId);
        UUID storyId = seedStoryInTenant(schemaName, projectId, sessionId);

        // Act
        ResponseEntity<String> res = client().get()
                .uri("/api/sessions/{s}/stories/{st}", sessionId, storyId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"id\":\"" + storyId + "\"");
        assertThat(res.getBody()).contains("\"sessionId\":\"" + sessionId + "\"");
    }

    @Test
    @DisplayName("should return 404 for a manually-created story (null sessionId) in session scope")
    void should_return_404_for_manual_story_in_session_scope() {
        // Arrange — create org + a manual story (no sessionId)
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        ResponseEntity<String> created = postManualStory(orgId, projectId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = created.getHeaders().getFirst("Location");
        String storyId = location.substring(location.lastIndexOf('/') + 1);

        // Act — try to access a manual story via a session scope
        ResponseEntity<String> res = client().get()
                .uri("/api/sessions/{s}/stories/{st}", UUID.randomUUID(), storyId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("USER_STORY_NOT_FOUND");
    }

    @Test
    @DisplayName("should return 404 for an unknown session on list")
    void should_return_404_for_unknown_session_on_list() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);

        ResponseEntity<String> res = client().get()
                .uri("/api/sessions/{s}/stories", UUID.randomUUID())
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("should list stories belonging to the session")
    void should_list_session_stories() {
        // Arrange
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        String schemaName = "tenant_acme-" + suffix;
        UUID projectId = UUID.randomUUID();
        UUID sessionId = createSession(orgId, projectId);
        seedStoryInTenant(schemaName, projectId, sessionId);
        seedStoryInTenant(schemaName, projectId, sessionId);

        // Act
        ResponseEntity<String> res = client().get()
                .uri("/api/sessions/{s}/stories", sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"totalElements\":2");
    }

    @Test
    @DisplayName("should reject an unauthenticated request")
    void should_reject_unauthenticated_request() {
        ResponseEntity<String> res = client().get()
                .uri("/api/sessions/{s}/stories/{st}", UUID.randomUUID(), UUID.randomUUID())
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private String createOrg(String suffix) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, "acme-" + suffix);
    }

    private UUID createSession(String orgId, UUID projectId) {
        ResponseEntity<String> res = client().post().uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Sprint planning", "language", "es-PE"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = res.getHeaders().getFirst("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    /** Inserts a story with the given sessionId directly into the tenant schema. */
    private UUID seedStoryInTenant(String schemaName, UUID projectId, UUID sessionId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO \"" + schemaName + "\".user_stories "
                        + "(id, project_id, session_id, title, role, action, benefit, priority, status, created_at, updated_at) "
                        + "VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, now(), now())",
                id.toString(), projectId.toString(), sessionId.toString(),
                "AI Story", "product manager", "generate a report", "track progress", "MEDIUM", "DRAFT");
        return id;
    }

    private ResponseEntity<String> postManualStory(String orgId, UUID projectId) {
        return client().post().uri("/api/projects/{p}/stories", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Manual story", "role", "user", "action", "do something",
                        "benefit", "get value", "priority", "LOW"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.bodyTo(String.class)), false);
    }
}
