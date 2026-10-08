package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubRequirementGenerationConfig;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end over HTTP and the database for the speakers of a session (US40): an uploaded recording whose
 * provider labelled two speakers keeps its utterances as segments, the speakers are listed as "Hablante N"
 * with the stretch where they talked over each other, the analyst names them and sets their side, and
 * processing then sends the AI the transcript tagged with those names and sides.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({SessionSpeakersIntegrationTest.DiarizedTranscription.class,
        SessionSpeakersIntegrationTest.CapturingGeneration.class})
@Tag("integration")
@DisplayName("Integration: Session speakers")
class SessionSpeakersIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000009";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicReference<String> PROCESSED_TRANSCRIPT = new AtomicReference<>();

    /** A recording where the client (speaker "0") and the team (speaker "1") overlap for one second. */
    @TestConfiguration
    static class DiarizedTranscription {
        @Bean
        @Primary
        TranscriptionPort diarizedTranscriptionPort() {
            return (_, _, _) -> new TranscriptionResult(
                    "Necesito que el comensal reserve una mesa en línea. ¿La reserva se paga por adelantado? "
                            + "Sí, con tarjeta.",
                    "es", 8_000L, 0.95, List.of(
                    new TranscriptionResult.SpeakerSegment("0",
                            "Necesito que el comensal reserve una mesa en línea.", 0, 4_000, 0.95),
                    new TranscriptionResult.SpeakerSegment("1",
                            "¿La reserva se paga por adelantado?", 3_000, 6_000, 0.9),
                    new TranscriptionResult.SpeakerSegment("0", "Sí, con tarjeta.", 6_100, 8_000, 0.95)));
        }
    }

    /** The shared stub stories, remembering the transcript the AI was given. */
    @TestConfiguration
    static class CapturingGeneration {
        @Bean
        @Primary
        RequirementGenerationPort capturingGenerationPort() {
            return new RequirementGenerationPort() {
                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public GenerationResult generate(String transcript, String language) {
                    PROCESSED_TRANSCRIPT.set(transcript);
                    return StubRequirementGenerationConfig.STUB_RESULT;
                }
            };
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("lists, names and sides the speakers of an uploaded meeting, and the AI reads the tagged transcript")
    void speakers_of_an_uploaded_meeting() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        createMember(orgId);
        UUID sessionId = createSession(orgId, projectId);
        upload(orgId, sessionId);
        String speakers = "/api/projects/" + projectId + "/sessions/" + sessionId + "/speakers";

        // The provider's speakers, by first appearance, and the second where they talked at once.
        ResponseEntity<String> listed = get(MEMBER_USER_ID, orgId, speakers);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(listed.getBody());
        assertThat(body.path("sessionId").asString()).isEqualTo(sessionId.toString());
        JsonNode first = body.path("speakers").get(0);
        assertThat(first.path("label").asString()).isEqualTo("0");
        assertThat(first.path("index").asInt()).isEqualTo(1);
        assertThat(first.path("name").asString()).isEqualTo("Hablante 1");
        assertThat(first.path("displayName").isNull()).isTrue();
        assertThat(first.path("side").isNull()).isTrue();
        assertThat(first.path("segmentCount").asInt()).isEqualTo(2);
        assertThat(body.path("speakers").get(1).path("name").asString()).isEqualTo("Hablante 2");
        JsonNode overlaps = body.path("overlaps");
        assertThat(overlaps.path("count").asInt()).isEqualTo(1);
        assertThat(overlaps.path("totalMs").asLong()).isEqualTo(1_000L);
        assertThat(overlaps.path("ranges").get(0).path("startMs").asLong()).isEqualTo(3_000L);
        assertThat(overlaps.path("ranges").get(0).path("endMs").asLong()).isEqualTo(4_000L);

        // The segments carry the speaker labels the history view renders.
        JsonNode segments = JSON.readTree(get(OWNER_USER_ID, orgId, "/api/sessions/" + sessionId + "/segments")
                .getBody()).path("segments");
        assertThat(segments.size()).isEqualTo(3);
        assertThat(segments.get(1).path("speakerLabel").asString()).isEqualTo("1");

        // The analyst names the client and puts the other speaker on the team.
        ResponseEntity<String> named = put(OWNER_USER_ID, orgId, speakers + "/0",
                body("  Ana  Torres ", "CLIENT"));
        assertThat(named.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode ana = JSON.readTree(named.getBody());
        assertThat(ana.path("name").asString()).isEqualTo("Ana Torres");
        assertThat(ana.path("side").asString()).isEqualTo("CLIENT");
        assertThat(ana.path("index").asInt()).isEqualTo(1);
        assertThat(put(OWNER_USER_ID, orgId, speakers + "/1", body(null, "TEAM")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode relisted = JSON.readTree(get(OWNER_USER_ID, orgId, speakers).getBody()).path("speakers");
        assertThat(relisted.get(0).path("displayName").asString()).isEqualTo("Ana Torres");
        assertThat(relisted.get(1).path("name").asString()).isEqualTo("Hablante 2");
        assertThat(relisted.get(1).path("side").asString()).isEqualTo("TEAM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"tenant_" + slug + "\".session_speakers WHERE session_id = ?::uuid",
                Integer.class, sessionId.toString())).isEqualTo(2);

        // A reader cannot rename; an unknown speaker, a bad side or another project's session are refused.
        assertThat(put(MEMBER_USER_ID, orgId, speakers + "/0", body("X", null)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> unknown = put(OWNER_USER_ID, orgId, speakers + "/7", body("X", null));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(unknown.getBody()).path("code").asString()).isEqualTo("SPEAKER_NOT_FOUND");
        assertThat(put(OWNER_USER_ID, orgId, speakers + "/0", body("X", "BOSS")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(OWNER_USER_ID, orgId,
                "/api/projects/" + UUID.randomUUID() + "/sessions/" + sessionId + "/speakers").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Processing sends the AI the transcript as speaker turns with the names and sides.
        ResponseEntity<String> processed = post(OWNER_USER_ID, orgId, "/api/sessions/" + sessionId + "/process");
        assertThat(processed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(PROCESSED_TRANSCRIPT.get()).isEqualTo("""
                [Ana Torres (Cliente)]: Necesito que el comensal reserve una mesa en línea.
                [Hablante 2 (Equipo)]: ¿La reserva se paga por adelantado?
                [Ana Torres (Cliente)]: Sí, con tarjeta.""");
    }

    // ----- helpers -----

    private static Map<String, Object> body(String displayName, String side) {
        Map<String, Object> body = new HashMap<>();
        body.put("displayName", displayName);
        body.put("side", side);
        return body;
    }

    private ResponseEntity<String> get(String userId, UUID orgId, String uri) {
        return client().get().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> put(String userId, UUID orgId, String uri, Map<String, Object> body) {
        return client().put().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> post(String userId, UUID orgId, String uri) {
        return client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> postJson(String userId, UUID orgId, String uri, Map<String, Object> body) {
        return client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private UUID createOrganization(String suffix, String slug) {
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, slug));
    }

    private void createMember(UUID orgId) {
        assertThat(postJson(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private UUID createProject(UUID orgId, String slug) {
        ResponseEntity<String> res = postJson(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects", Map.of(
                "name", "Speakers Project",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Hexagonal",
                "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Speakers Project"));
    }

    private UUID createSession(UUID orgId, UUID projectId) {
        ResponseEntity<String> res = postJson(OWNER_USER_ID, orgId, "/api/projects/" + projectId + "/sessions",
                Map.of("title", "Kickoff con el restaurante", "language", "es-PE"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(JSON.readTree(res.getBody()).path("id").asString());
    }

    private void upload(UUID orgId, UUID sessionId) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource("fake-audio".getBytes()) {
            @Override
            public String getFilename() {
                return "kickoff.wav";
            }
        });
        ResponseEntity<String> res = client().post().uri("/api/sessions/{id}/upload", sessionId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
