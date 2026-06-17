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
 * End-to-end integration tests for the full discovery-session lifecycle REST API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Discovery Session Lifecycle")
class DiscoverySessionLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should transition session through start -> pause -> resume -> stop recording")
    void should_manage_session_recording_lifecycle() {
        // 1. Arrange — create an org (provisions schema)
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        UUID projectId = UUID.randomUUID();

        // 2. Create a session (DRAFT)
        ResponseEntity<String> createRes = client().post().uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Lifecycle Meeting", "language", "es-PE"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String body = createRes.getBody();
        assertThat(body).isNotNull();
        String sessionIdString = body.split("\"id\":\"")[1].split("\"")[0];
        UUID sessionId = UUID.fromString(sessionIdString);

        // 3. Start Recording (DRAFT -> RECORDING)
        ResponseEntity<String> startRes = client().post().uri("/api/projects/{p}/sessions/{s}/start", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)));
        assertThat(startRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(startRes.getBody()).contains("\"status\":\"RECORDING\"");

        // Verify status in DB
        assertDatabaseStatus(expectedSlug, sessionId, "RECORDING");

        // 4. Pause Recording (RECORDING -> PAUSED)
        ResponseEntity<String> pauseRes = client().post().uri("/api/projects/{p}/sessions/{s}/pause", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)));
        assertThat(pauseRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pauseRes.getBody()).contains("\"status\":\"PAUSED\"");

        assertDatabaseStatus(expectedSlug, sessionId, "PAUSED");

        // 5. Resume Recording (PAUSED -> RECORDING)
        ResponseEntity<String> resumeRes = client().post().uri("/api/projects/{p}/sessions/{s}/resume", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)));
        assertThat(resumeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resumeRes.getBody()).contains("\"status\":\"RECORDING\"");

        assertDatabaseStatus(expectedSlug, sessionId, "RECORDING");

        // 6. Stop Recording (RECORDING -> STOPPED)
        ResponseEntity<String> stopRes = client().post().uri("/api/projects/{p}/sessions/{s}/stop", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)));
        assertThat(stopRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stopRes.getBody()).contains("\"status\":\"STOPPED\"");
        assertThat(stopRes.getBody()).contains("\"endedAt\":");

        assertDatabaseStatus(expectedSlug, sessionId, "STOPPED");

        // 7. Verify Unhappy Path (Try pausing a STOPPED session)
        ResponseEntity<String> invalidTransitionRes = client().post().uri("/api/projects/{p}/sessions/{s}/pause", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(String.class)));
        assertThat(invalidTransitionRes.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(invalidTransitionRes.getBody()).contains("INVALID_SESSION_TRANSITION");
    }

    private void assertDatabaseStatus(String schemaSlug, UUID sessionId, String expectedStatus) {
        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM \"tenant_" + schemaSlug + "\".discovery_sessions WHERE id = ?::uuid",
                String.class, sessionId.toString());
        assertThat(currentStatus).isEqualTo(expectedStatus);
    }
}
