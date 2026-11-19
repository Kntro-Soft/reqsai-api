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
 * US70 over HTTP and tenant routing: the analyst confirms the participants' consent to the recording
 * (Ley N.° 29733) before the live capture starts; without it the capture does not start.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Recording consent (US70)")
class RecordingConsentIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Given an analyst starting a capture session, When they confirm the participants' consent, "
            + "Then the system records the confirmation with the date and the user, And starts the capture")
    void confirming_the_consent_records_it_and_starts_the_capture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionId = createSession(orgId, projectId);
        String base = "/api/projects/" + projectId + "/sessions/" + sessionId;

        ResponseEntity<String> consent = post(orgId, base + "/consent");

        assertThat(consent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consent.getBody()).contains("\"recordingConsentBy\":\"" + USER_ID + "\"");
        assertThat(consent.getBody()).containsPattern("\"recordingConsentAt\":\"\\d{4}-");
        Map<String, Object> row = consentRow("acme-" + suffix, sessionId);
        assertThat(row.get("recording_consent_by")).hasToString(USER_ID);
        assertThat(row.get("recording_consent_at")).isNotNull();

        ResponseEntity<String> start = post(orgId, base + "/start");

        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(start.getBody()).contains("\"status\":\"RECORDING\"");
        assertThat(start.getBody()).contains("\"recordingConsentBy\":\"" + USER_ID + "\"");
        assertThat(consentRow("acme-" + suffix, sessionId).get("status")).isEqualTo("RECORDING");
    }

    @Test
    @DisplayName("Given an analyst starting a capture session, When they do not confirm consent, "
            + "Then the system does not start the capture, And shows the reason for the block")
    void starting_without_the_consent_is_blocked_with_the_reason() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionId = createSession(orgId, projectId);

        ResponseEntity<String> start = post(orgId, "/api/projects/" + projectId + "/sessions/" + sessionId + "/start");

        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(start.getBody()).contains("RECORDING_CONSENT_REQUIRED").contains("consent");
        Map<String, Object> row = consentRow("acme-" + suffix, sessionId);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("recording_consent_at")).isNull();
        assertThat(row.get("recording_consent_by")).isNull();
    }

    @Test
    @DisplayName("keeps the first confirmation when it is confirmed again, and refuses it once the capture has started")
    void consent_is_idempotent_and_only_before_the_capture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionId = createSession(orgId, projectId);
        String base = "/api/projects/" + projectId + "/sessions/" + sessionId;

        assertThat(post(orgId, base + "/consent").getStatusCode()).isEqualTo(HttpStatus.OK);
        Object firstAt = consentRow("acme-" + suffix, sessionId).get("recording_consent_at");
        assertThat(post(orgId, base + "/consent").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consentRow("acme-" + suffix, sessionId).get("recording_consent_at")).isEqualTo(firstAt);

        assertThat(post(orgId, base + "/start").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> late = post(orgId, base + "/consent");
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(late.getBody()).contains("INVALID_SESSION_STATUS");
    }

    @Test
    @DisplayName("answers 404 SESSION_NOT_FOUND when the session does not exist")
    void unknown_session_is_not_found() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);

        ResponseEntity<String> res = post(orgId,
                "/api/projects/" + UUID.randomUUID() + "/sessions/" + UUID.randomUUID() + "/consent");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("SESSION_NOT_FOUND");
    }

    private Map<String, Object> consentRow(String slug, UUID sessionId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, recording_consent_at, recording_consent_by FROM \"tenant_" + slug
                        + "\".discovery_sessions WHERE id = ?::uuid", sessionId.toString());
    }

    private String createOrg(String suffix) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((_, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, "acme-" + suffix);
    }

    private UUID createSession(String orgId, UUID projectId) {
        ResponseEntity<String> created = client().post().uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Consent Meeting", "language", "es-PE"))
                .exchange((_, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"recordingConsentAt\":null");
        return UUID.fromString(created.getBody().split("\"id\":\"")[1].split("\"")[0]);
    }

    private ResponseEntity<String> post(String orgId, String uri) {
        return client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((_, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }
}
