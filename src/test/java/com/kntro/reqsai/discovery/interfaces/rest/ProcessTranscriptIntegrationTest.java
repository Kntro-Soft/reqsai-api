package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubRequirementGenerationConfig;
import com.kntro.reqsai.testsupport.StubTranscriptionConfig;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for {@code POST /api/sessions/{id}/process} (TS09) and
 * {@code GET /api/sessions/{id}/transcript} (TS10).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@Import({StubTranscriptionConfig.class, StubRequirementGenerationConfig.class})
@DisplayName("Integration: Process Transcript")
class ProcessTranscriptIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    @DisplayName("should process transcript and return COMPLETED session with stories")
    void should_process_and_complete() {
        // Arrange
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgToken = provisionTenant(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionId = createAndUploadSession(orgToken, projectId);

        // Act
        ResponseEntity<String> res = client().post()
                .uri("/api/sessions/{id}/process", sessionId)
                .header("Authorization", orgToken)
                .header("Api-Version", "1")
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"COMPLETED\"");
        assertThat(res.getBody()).contains("\"stories\"");
    }

    @Test
    @DisplayName("should return transcript via GET /transcript")
    void should_get_transcript() {
        // Arrange
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgToken = provisionTenant(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionId = createAndUploadSession(orgToken, projectId);

        // Act
        ResponseEntity<String> res = client().get()
                .uri("/api/sessions/{id}/transcript", sessionId)
                .header("Authorization", orgToken)
                .header("Api-Version", "1")
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("transcript");
    }

    @Test
    @DisplayName("should return 422 when session is not STOPPED")
    void should_return_422_when_not_stopped() {
        // Arrange
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgToken = provisionTenant(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionId = createSession(orgToken, projectId); // DRAFT — transcript not uploaded

        // Act
        ResponseEntity<String> res = client().post()
                .uri("/api/sessions/{id}/process", sessionId)
                .header("Authorization", orgToken)
                .header("Api-Version", "1")
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)), false);

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String provisionTenant(String suffix) {
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = res.getBody();
        assertThat(body).isNotNull();
        String orgId = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        return TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER");
    }

    private UUID createSession(String orgToken, UUID projectId) {
        ResponseEntity<String> res = client().post()
                .uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", orgToken).header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Demo session", "language", "es-PE"))
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = res.getBody();
        assertThat(body).isNotNull();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private UUID createAndUploadSession(String orgToken, UUID projectId) {
        UUID sessionId = createSession(orgToken, projectId);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("fake-audio".getBytes()) {
            @Override
            public String getFilename() { return "meeting.mp3"; }
        });
        ResponseEntity<String> res = client().post()
                .uri("/api/sessions/{id}/upload", sessionId)
                .header("Authorization", orgToken).header("Api-Version", "1")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body)
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return sessionId;
    }
}
