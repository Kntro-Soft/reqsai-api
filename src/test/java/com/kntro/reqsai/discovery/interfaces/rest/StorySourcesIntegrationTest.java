package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.StoryInsight;
import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end over HTTP and the database for {@code GET /api/projects/{projectId}/stories/{storyId}/sources}
 * (US72): a recording is uploaded and processed, so one generated story keeps the quote the model took it from
 * and the sources endpoint places it in the transcript with the moment of the meeting and who said it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({StorySourcesIntegrationTest.DiarizedTranscription.class,
        StorySourcesIntegrationTest.QuotingGeneration.class})
@Tag("integration")
@DisplayName("Integration: Story sources")
class StorySourcesIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000009";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A recording of two speakers: the client ("0") asks for online booking, the team ("1") asks about payment. */
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
                            "¿La reserva se paga por adelantado?", 4_000, 6_000, 0.9),
                    new TranscriptionResult.SpeakerSegment("0", "Sí, con tarjeta.", 6_100, 8_000, 0.95)));
        }
    }

    /** Two stories: one with the verbatim quote it was taken from, one the model gave without evidence. */
    @TestConfiguration
    static class QuotingGeneration {
        @Bean
        @Primary
        RequirementGenerationPort quotingGenerationPort() {
            return new RequirementGenerationPort() {
                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public GenerationResult generate(String transcript, String language) {
                    return new GenerationResult(List.of(
                            new GenerationResult.GeneratedStory("Reservar una mesa en línea", "comensal",
                                    "reservar una mesa desde la web", "no llamar por teléfono", Priority.HIGH, 3,
                                    List.of())
                                    .withInsight(StoryInsight.evidence("que el comensal reserve una mesa en línea")),
                            new GenerationResult.GeneratedStory("Pagar la reserva con tarjeta", "comensal",
                                    "pagar la reserva con tarjeta", "confirmarla al instante", Priority.MEDIUM, 2,
                                    List.of())));
                }
            };
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Given a story generated during a session, when the user asks for its origin, then it shows the transcript fragment with the moment of the meeting")
    void a_generated_story_shows_the_fragment_it_comes_from() {
        Fixture f = processedMeeting();
        // The analyst names the speaker: the fragment is attributed to that name.
        assertThat(put(OWNER_USER_ID, f.orgId, "/api/projects/" + f.projectId + "/sessions/" + f.sessionId
                + "/speakers/0", Map.of("displayName", "Ana Torres", "side", "CLIENT")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> res = get(MEMBER_USER_ID, f.orgId, sources(f.projectId, f.storyId("Reservar una mesa en línea")));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(res.getBody());
        assertThat(body.path("origin").asString()).isEqualTo("SESSION");
        assertThat(body.path("storyId").asString()).isEqualTo(f.storyId("Reservar una mesa en línea").toString());
        assertThat(body.path("fragments")).hasSize(1);
        JsonNode fragment = body.path("fragments").get(0);
        assertThat(fragment.path("sessionId").asString()).isEqualTo(f.sessionId.toString());
        assertThat(fragment.path("sessionTitle").asString()).isEqualTo("Kickoff con el restaurante");
        assertThat(fragment.path("sessionStartedAt").asString()).isNotBlank();
        assertThat(fragment.path("quote").asString()).isEqualTo("que el comensal reserve una mesa en línea");
        assertThat(fragment.path("transcriptText").asString())
                .isEqualTo("Necesito que el comensal reserve una mesa en línea.");
        assertThat(fragment.path("startSequence").asInt()).isEqualTo(1);
        assertThat(fragment.path("endSequence").asInt()).isEqualTo(1);
        assertThat(fragment.path("startMs").asLong()).isEqualTo(0L);
        assertThat(fragment.path("endMs").asLong()).isEqualTo(4_000L);
        assertThat(fragment.path("occurredAt").asString()).isNotBlank();
        assertThat(fragment.path("speakerName").asString()).isEqualTo("Ana Torres");
        assertThat(count(f.slug, f.storyId("Reservar una mesa en línea"))).isEqualTo(1);
    }

    @Test
    @DisplayName("Given a story created manually, when the user asks for its origin, then the system says it does not come from a session")
    void a_manual_story_does_not_come_from_a_session() {
        Fixture f = processedMeeting();
        ResponseEntity<String> created = postJson(OWNER_USER_ID, f.orgId,
                "/api/projects/" + f.projectId + "/stories", Map.of("title", "Exportar el menú", "role", "gerente",
                        "action", "exportar el menú a PDF", "benefit", "imprimirlo", "priority", "LOW"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID storyId = UUID.fromString(JSON.readTree(created.getBody()).path("id").asString());

        ResponseEntity<String> res = get(OWNER_USER_ID, f.orgId, sources(f.projectId, storyId));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(res.getBody());
        assertThat(body.path("origin").asString()).isEqualTo("MANUAL");
        assertThat(body.path("sessionId").isNull()).isTrue();
        assertThat(body.path("fragments")).isEmpty();
    }

    @Test
    @DisplayName("A story generated in a session without a stored fragment reports its origin as unavailable, not an error")
    void a_historical_story_reports_an_unavailable_origin() {
        Fixture f = processedMeeting();

        ResponseEntity<String> res = get(OWNER_USER_ID, f.orgId, sources(f.projectId, f.storyId("Pagar la reserva con tarjeta")));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(res.getBody());
        assertThat(body.path("origin").asString()).isEqualTo("UNAVAILABLE");
        assertThat(body.path("sessionId").asString()).isEqualTo(f.sessionId.toString());
        assertThat(body.path("sessionTitle").asString()).isEqualTo("Kickoff con el restaurante");
        assertThat(body.path("fragments")).isEmpty();
    }

    @Test
    @DisplayName("should 404 for an unknown story or one of another project, and refuse unauthenticated callers")
    void should_refuse_unknown_stories_and_anonymous_callers() {
        Fixture f = processedMeeting();
        UUID storyId = f.storyId("Reservar una mesa en línea");

        ResponseEntity<String> unknown = get(OWNER_USER_ID, f.orgId, sources(f.projectId, UUID.randomUUID()));
        ResponseEntity<String> otherProject = get(OWNER_USER_ID, f.orgId, sources(UUID.randomUUID(), storyId));
        ResponseEntity<String> anonymous = client().get().uri(sources(f.projectId, storyId))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)), false);

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(unknown.getBody()).path("code").asString()).isEqualTo("USER_STORY_NOT_FOUND");
        assertThat(otherProject.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(anonymous.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("deleting a story removes its source fragments")
    void deleting_a_story_removes_its_fragments() {
        Fixture f = processedMeeting();
        UUID storyId = f.storyId("Reservar una mesa en línea");
        assertThat(count(f.slug, storyId)).isEqualTo(1);

        ResponseEntity<String> deleted = client().delete()
                .uri("/api/projects/{p}/stories/{s}", f.projectId, storyId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, f.orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count(f.slug, storyId)).isZero();
    }

    // ----- helpers -----

    private record Fixture(String slug, UUID orgId, UUID projectId, UUID sessionId, JdbcTemplate jdbc) {
        UUID storyId(String title) {
            return UUID.fromString(jdbc.queryForObject(
                    "SELECT id::text FROM \"tenant_" + slug + "\".user_stories WHERE project_id = ?::uuid AND title = ?",
                    String.class, projectId.toString(), title));
        }
    }

    private int count(String slug, UUID storyId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM \"tenant_" + slug
                + "\".story_source_fragments WHERE story_id = ?::uuid", Integer.class, storyId.toString());
    }

    /** An organization with a project and a member, and an uploaded, diarized and processed meeting. */
    private Fixture processedMeeting() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        createMember(orgId);
        UUID sessionId = createSession(orgId, projectId);
        upload(orgId, sessionId);
        ResponseEntity<String> processed = post(OWNER_USER_ID, orgId, "/api/sessions/" + sessionId + "/process");
        assertThat(processed.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Fixture(slug, orgId, projectId, sessionId, jdbcTemplate);
    }

    private static String sources(UUID projectId, UUID storyId) {
        return "/api/projects/" + projectId + "/stories/" + storyId + "/sources";
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
        assertThat(postJson(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", new HashMap<>(Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER")))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private UUID createProject(UUID orgId, String slug) {
        ResponseEntity<String> res = postJson(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects", Map.of(
                "name", "Sources Project",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Hexagonal",
                "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Sources Project"));
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
