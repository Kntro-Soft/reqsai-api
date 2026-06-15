package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for GET /api/projects/{projectId}/sessions and GET /{sessionId}.
 * Creates a tenant org, a session, then exercises the read endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Get Discovery Session")
class GetDiscoverySessionIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should return a session by id")
    void should_return_session_by_id() {
        // Arrange — create org + session
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        ResponseEntity<String> created = postSession(orgId, projectId, "Sprint 1");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String location = created.getHeaders().getFirst("Location");
        assertThat(location).isNotNull();
        String sessionId = location.substring(location.lastIndexOf('/') + 1);

        // Act
        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/sessions/{s}", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"id\":\"" + sessionId + "\"");
        assertThat(res.getBody()).contains("\"status\":\"DRAFT\"");
        assertThat(res.getBody()).contains("\"title\":\"Sprint 1\"");
    }

    @Test
    @DisplayName("should return 404 for an unknown session id")
    void should_return_404_for_unknown_session() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();

        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/sessions/{s}", projectId, UUID.randomUUID())
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("should list all sessions for a project")
    void should_list_sessions_for_project() {
        // Arrange — create org + two sessions
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        assertThat(postSession(orgId, projectId, "Sprint A").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postSession(orgId, projectId, "Sprint B").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Act
        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"totalElements\":2");
        assertThat(res.getBody()).contains("Sprint A");
        assertThat(res.getBody()).contains("Sprint B");
    }

    @Test
    @DisplayName("should reject an unauthenticated request on GET /{sessionId}")
    void should_reject_unauthenticated_get() {
        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/sessions/{s}", UUID.randomUUID(), UUID.randomUUID())
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

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

    private ResponseEntity<String> postSession(String orgId, UUID projectId, String title) {
        return client().post().uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title, "language", "es-PE"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.bodyTo(String.class)));
    }
}
