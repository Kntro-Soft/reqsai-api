package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end over HTTP and the database for sharing a discovery session: the analyst opens a spectator or a
 * reviewer link, an anonymous visitor follows the session, only the reviewer decides, and a revoked or
 * mismatched link answers like an unknown one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Share a discovery session")
class ShareSessionIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000009";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a spectator follows the session; a reviewer decides; links of other kinds do not open it")
    void follow_and_decide() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug, "Sesión Compartida");
        UUID otherProjectId = createProject(orgId, slug, "Otro Proyecto");
        createMember(orgId, Map.of("userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Member", "role", "MEMBER"));
        String schema = "\"tenant_" + slug + "\".";
        UUID sessionId = insertSession(schema, projectId, "Reunión con el cliente");
        UUID otherSession = insertSession(schema, otherProjectId, "Reunión ajena");
        insertSegment(schema, sessionId, 1, "SPEAKER_1", "Necesitamos reservar mesas");
        insertSegment(schema, sessionId, 2, "SPEAKER_2", "Y recibir un recordatorio");
        UUID toAccept = insertSuggestion(schema, projectId, sessionId, "Reservar mesa");
        UUID toDismiss = insertSuggestion(schema, projectId, sessionId, "Pagar con cripto");
        UUID sessionStory = insertStory(schema, projectId, sessionId, "Recordatorio de reserva");
        UUID outsideStory = insertStory(schema, projectId, null, "Historia manual");
        String links = "/api/projects/" + projectId + "/share-links";

        // A read-only member cannot share; a link needs a session of the same project.
        Map<String, Object> viewBody = Map.of("sessionId", sessionId.toString(), "access", "VIEW", "days", 3);
        assertThat(send(HttpMethod.POST, MEMBER_USER_ID, orgId, links, viewBody).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, links,
                Map.of("sessionId", otherSession.toString())).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode view = created(send(HttpMethod.POST, OWNER_USER_ID, orgId, links, viewBody));
        JsonNode review = created(send(HttpMethod.POST, OWNER_USER_ID, orgId, links,
                Map.of("sessionId", sessionId.toString(), "access", "REVIEW")));
        JsonNode backlog = created(send(HttpMethod.POST, OWNER_USER_ID, orgId, links, Map.of()));
        assertThat(view.path("access").asString()).isEqualTo("VIEW");
        assertThat(view.path("sessionId").asString()).isEqualTo(sessionId.toString());
        assertThat(backlog.path("sessionId").isNull()).isTrue();
        String viewToken = view.path("token").asString();
        String reviewToken = review.path("token").asString();

        // The spectator sees the session, its transcript, pending suggestions and stories.
        JsonNode shared = JSON.readTree(anonymous(HttpMethod.GET, "/api/share/session/" + viewToken, null).getBody());
        assertThat(shared.path("projectName").asString()).isEqualTo("Sesión Compartida");
        assertThat(shared.path("access").asString()).isEqualTo("VIEW");
        assertThat(shared.path("session").path("title").asString()).isEqualTo("Reunión con el cliente");
        assertThat(shared.path("segments").size()).isEqualTo(2);
        assertThat(shared.path("segments").get(0).path("text").asString()).isEqualTo("Necesitamos reservar mesas");
        assertThat(shared.path("totalSegments").asLong()).isEqualTo(2);
        assertThat(shared.path("suggestions").size()).isEqualTo(2);
        assertThat(shared.path("stories").size()).isEqualTo(1);

        // A spectator cannot decide.
        String base = "/api/share/session/" + viewToken;
        for (ResponseEntity<String> attempt : List.of(
                anonymous(HttpMethod.POST, base + "/suggestions/" + toAccept + "/accept", Map.of("approve", true)),
                anonymous(HttpMethod.POST, base + "/suggestions/" + toDismiss + "/dismiss", Map.of()),
                anonymous(HttpMethod.POST, base + "/stories/" + sessionStory + "/status", Map.of("status", "APPROVED")))) {
            assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(JSON.readTree(attempt.getBody()).path("code").asString()).isEqualTo("SHARE_LINK_READ_ONLY");
        }

        // A backlog link does not open a session, and a session link does not open the backlog.
        assertThat(anonymous(HttpMethod.GET, "/api/share/session/" + backlog.path("token").asString(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(anonymous(HttpMethod.GET, "/api/share/" + viewToken, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // The reviewer accepts with approval, dismisses, and decides on the session's story only.
        String decide = "/api/share/session/" + reviewToken;
        ResponseEntity<String> accepted = anonymous(HttpMethod.POST,
                decide + "/suggestions/" + toAccept + "/accept", Map.of("approve", true));
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(accepted.getBody()).path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(anonymous(HttpMethod.POST, decide + "/suggestions/" + toDismiss + "/dismiss", Map.of())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> approved = anonymous(HttpMethod.POST,
                decide + "/stories/" + sessionStory + "/status", Map.of("status", "APPROVED"));
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(approved.getBody()).path("status").asString()).isEqualTo("APPROVED");
        assertThat(anonymous(HttpMethod.POST, decide + "/stories/" + outsideStory + "/status",
                Map.of("status", "APPROVED")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(anonymous(HttpMethod.POST, decide + "/suggestions/" + UUID.randomUUID() + "/dismiss", Map.of())
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // The spectator sees the result on the next read.
        JsonNode after = JSON.readTree(anonymous(HttpMethod.GET, base, null).getBody());
        assertThat(after.path("suggestions").size()).isZero();
        assertThat(after.path("stories").size()).isEqualTo(2);

        // The team lists both links; revoking closes one.
        JsonNode listed = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, links, null).getBody());
        assertThat(listed.size()).isEqualTo(3);
        ResponseEntity<String> revoked = send(HttpMethod.DELETE, OWNER_USER_ID, orgId,
                links + "/" + view.path("id").asString(), null);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> closed = anonymous(HttpMethod.GET, base, null);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(closed.getBody()).path("code").asString()).isEqualTo("SHARE_LINK_UNAVAILABLE");
    }

    // ----- helpers -----

    private JsonNode created(ResponseEntity<String> res) {
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JSON.readTree(res.getBody());
    }

    private UUID insertSession(String schema, UUID projectId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + schema + "discovery_sessions (id, project_id, title, language, status,"
                + " created_at, updated_at) VALUES (?, ?, ?, 'es-PE', 'STOPPED', now(), now())", id, projectId, title);
        return id;
    }

    private void insertSegment(String schema, UUID sessionId, int sequence, String speaker, String text) {
        jdbcTemplate.update("INSERT INTO " + schema + "transcript_segments (id, session_id, sequence, speaker_label,"
                        + " text, start_ms, end_ms, is_final, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, true, now(), now())",
                UUID.randomUUID(), sessionId, sequence, speaker, text, sequence * 1000, sequence * 1000 + 900);
    }

    private UUID insertSuggestion(String schema, UUID projectId, UUID sessionId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + schema + "suggestions (id, session_id, project_id, type, status,"
                + " draft_title, draft_role, draft_action, draft_benefit, draft_priority, draft_story_points,"
                + " created_at, updated_at) VALUES (?, ?, ?, 'NEW_STORY', 'PENDING', ?, 'cliente', 'reservar',"
                + " 'ahorrar tiempo', 'HIGH', 3, now(), now())", id, sessionId, projectId, title);
        return id;
    }

    private UUID insertStory(String schema, UUID projectId, UUID sessionId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + schema + "user_stories (id, project_id, session_id, title, role, action,"
                + " benefit, priority, story_points, status, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, 'cliente', 'recibir un aviso', 'no olvidar', 'MEDIUM', 2, 'DRAFT',"
                + " now(), now())", id, projectId, sessionId, title);
        return id;
    }

    private ResponseEntity<String> send(HttpMethod method, String userId, UUID orgId, String uri,
                                        Map<String, Object> body) {
        var spec = client().method(method).uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1");
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> anonymous(HttpMethod method, String uri, Map<String, Object> body) {
        var spec = client().method(method).uri(uri).header("Api-Version", "1");
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
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

    private void createMember(UUID orgId, Map<String, Object> body) {
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", body)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private UUID createProject(UUID orgId, String slug, String name) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "name", name,
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Hexagonal",
                "domain", "Gastronomía"));
        ResponseEntity<String> res = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/projects", body);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), name));
    }
}
