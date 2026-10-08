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
 * End-to-end over HTTP and the database for US50: the analyst shares the backlog through a link, an
 * anonymous client opens it, approves and comments on stories, the team reads that feedback, and a
 * revoked link stops working.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Share stories with the client")
class ShareWithClientIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000009";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a client opens the link, approves and comments; the team reads it; revoking closes the link")
    void share_review_and_revoke() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        createMember(orgId, Map.of("userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Member", "role", "MEMBER"));
        String shown = createStory(orgId, projectId, "Reservar mesa en línea");
        String rejected = createStory(orgId, projectId, "Pagar con criptomonedas");
        // The JDK client of the test cannot send PATCH; the review decision itself is covered elsewhere.
        jdbcTemplate.update("UPDATE \"tenant_" + slug + "\".user_stories SET status = 'REJECTED' WHERE id = ?::uuid",
                rejected);
        String links = "/api/projects/" + projectId + "/share-links";

        // A read-only member cannot share; the owner can, and gets the token once.
        assertThat(send(HttpMethod.POST, MEMBER_USER_ID, orgId, links, Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> created = send(HttpMethod.POST, OWNER_USER_ID, orgId, links, Map.of("days", 7));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode link = JSON.readTree(created.getBody());
        String token = link.path("token").asString();
        assertThat(token).hasSize(64);
        assertThat(link.path("active").asBoolean()).isTrue();
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, links, Map.of("days", 91)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // The listing never repeats the token.
        JsonNode listed = JSON.readTree(send(HttpMethod.GET, MEMBER_USER_ID, orgId, links, null).getBody());
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).path("token").isNull()).isTrue();

        // The anonymous client sees the project and the stories under review, not the rejected one.
        ResponseEntity<String> opened = anonymous(HttpMethod.GET, "/api/share/" + token, null);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode backlog = JSON.readTree(opened.getBody());
        assertThat(backlog.path("projectName").asString()).isEqualTo("Share Project");
        assertThat(backlog.path("stories").size()).isEqualTo(1);
        assertThat(backlog.path("stories").get(0).path("id").asString()).isEqualTo(shown);

        // The client comments and approves; a comment without text and a hidden story are refused.
        String feedback = "/api/share/" + token + "/stories/" + shown + "/feedback";
        assertThat(anonymous(HttpMethod.POST, feedback, Map.of("kind", "COMMENT", "authorName", "María Quispe",
                "comment", "La cancelación debería ser hasta 24 horas antes")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(anonymous(HttpMethod.POST, feedback, Map.of("kind", "APPROVAL", "authorName", "María Quispe"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(anonymous(HttpMethod.POST, feedback, Map.of("kind", "COMMENT", "authorName", "María"))
                .getStatusCode().is4xxClientError()).isTrue();
        assertThat(anonymous(HttpMethod.POST, "/api/share/" + token + "/stories/" + rejected + "/feedback",
                Map.of("kind", "APPROVAL", "authorName", "María")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // The client sees its own feedback on reload; the team reads it on the story.
        JsonNode reloaded = JSON.readTree(anonymous(HttpMethod.GET, "/api/share/" + token, null).getBody());
        assertThat(reloaded.path("stories").get(0).path("feedback").size()).isEqualTo(2);
        JsonNode team = JSON.readTree(send(HttpMethod.GET, MEMBER_USER_ID, orgId,
                "/api/projects/" + projectId + "/stories/" + shown + "/client-feedback", null).getBody());
        assertThat(team.size()).isEqualTo(2);
        assertThat(team.get(0).path("kind").asString()).isEqualTo("COMMENT");
        assertThat(team.get(1).path("kind").asString()).isEqualTo("APPROVAL");
        // Client feedback never changes the story's review status.
        assertThat(JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/stories/" + shown, null).getBody()).path("status").asString())
                .isEqualTo("DRAFT");

        // Revoking closes the link; an unknown token answers the same.
        String linkId = link.path("id").asString();
        ResponseEntity<String> revoked = send(HttpMethod.DELETE, OWNER_USER_ID, orgId, links + "/" + linkId, null);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(revoked.getBody()).path("active").asBoolean()).isFalse();
        ResponseEntity<String> closed = anonymous(HttpMethod.GET, "/api/share/" + token, null);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(closed.getBody()).path("code").asString()).isEqualTo("SHARE_LINK_UNAVAILABLE");
        assertThat(anonymous(HttpMethod.GET, "/api/share/" + "f".repeat(64), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.DELETE, OWNER_USER_ID, orgId, links + "/" + UUID.randomUUID(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----- helpers -----

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

    private String createStory(UUID orgId, UUID projectId, String title) {
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "role", "cliente",
                "action", title.toLowerCase(), "benefit", "ahorrar tiempo", "priority", "HIGH"));
        ResponseEntity<String> res = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/stories", body);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JSON.readTree(res.getBody()).path("id").asString();
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

    private UUID createProject(UUID orgId, String slug) {
        ResponseEntity<String> res = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/projects", Map.of(
                        "name", "Share Project",
                        "programmingLanguages", List.of("Java"),
                        "frameworks", List.of("Spring Boot"),
                        "clientPlatforms", List.of("Web"),
                        "databases", List.of("PostgreSQL"),
                        "architecture", "Hexagonal",
                        "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Share Project"));
    }
}
