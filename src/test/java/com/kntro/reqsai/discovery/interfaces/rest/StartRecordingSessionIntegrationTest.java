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
 * End-to-end integration tests for the start-recording slice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Start Recording Session")
class StartRecordingSessionIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should transition session to RECORDING status and update startedAt")
    void should_transition_session_to_recording() {
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

        // 2. Create a session (it starts in DRAFT)
        ResponseEntity<String> createRes = client().post().uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Reunión de prueba", "language", "es-PE"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Extract session ID
        String body = createRes.getBody();
        assertThat(body).isNotNull();
        String sessionIdString = body.split("\"id\":\"")[1].split("\"")[0];
        UUID sessionId = UUID.fromString(sessionIdString);

        // 3. Act — transition to RECORDING
        ResponseEntity<String> startRes = client().post().uri("/api/projects/{p}/sessions/{s}/start", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // 4. Assert — HTTP Response
        assertThat(startRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(startRes.getBody()).contains("\"status\":\"RECORDING\"");
        assertThat(startRes.getBody()).contains("\"startedAt\":");

        // Assert — Database row state is RECORDING in the tenant's schema
        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM \"tenant_" + expectedSlug + "\".discovery_sessions WHERE id = ?::uuid",
                String.class, sessionId.toString());
        assertThat(currentStatus).isEqualTo("RECORDING");

        // 5. Act again — try to transition again and verify error (Unhappy Path)
        ResponseEntity<String> restartRes = client().post().uri("/api/projects/{p}/sessions/{s}/start", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(restartRes.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(restartRes.getBody()).contains("INVALID_SESSION_TRANSITION");
    }
}
