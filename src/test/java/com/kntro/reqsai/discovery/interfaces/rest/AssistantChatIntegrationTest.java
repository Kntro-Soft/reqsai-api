package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubRequirementGenerationConfig;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end over HTTP and the database for the assistant chat: a requirement typed with no live session
 * becomes project-level suggestions in the reply, the chat is listed back, and those suggestions are
 * accepted or dismissed through the project-scoped endpoints. The model is the shared stub, whose default
 * {@code converse} treats the whole message as a requirement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubRequirementGenerationConfig.class)
@Tag("integration")
@DisplayName("Integration: Assistant chat")
class AssistantChatIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000009";
    private static final String APPROVER_USER_ID = "00000000-0000-0000-0000-00000000000a";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a typed requirement becomes session-less suggestions the analyst accepts or dismisses from the chat")
    void chat_requirement_to_reviewed_suggestions() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        createMember(orgId, Map.of("userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Member", "role", "MEMBER"));
        String chat = "/api/projects/" + projectId + "/assistant/messages";

        ResponseEntity<String> sent = post(OWNER_USER_ID, orgId, chat,
                Map.of("content", "Quiero que el usuario inicie sesión con su cuenta de Google"));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode exchange = JSON.readTree(sent.getBody());
        assertThat(exchange.path("question").path("role").asString()).isEqualTo("ANALYST");
        assertThat(exchange.path("answer").path("role").asString()).isEqualTo("ASSISTANT");
        assertThat(exchange.path("answer").path("content").asString()).isNotBlank();
        JsonNode suggestions = exchange.path("answer").path("suggestions");
        assertThat(suggestions.size()).isPositive();
        suggestions.forEach(s -> {
            assertThat(s.path("sessionId").isNull()).isTrue();
            assertThat(s.path("status").asString()).isEqualTo("PENDING");
        });

        // A member with the READ floor reads the chat but cannot write to it.
        assertThat(post(MEMBER_USER_ID, orgId, chat, Map.of("content", "hola")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> listed = get(MEMBER_USER_ID, orgId, chat);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(listed.getBody()).size()).isEqualTo(2);

        // Accept the first suggestion from the chat: it becomes a backlog story.
        String first = suggestions.get(0).path("id").asString();
        ResponseEntity<String> accepted = post(OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/suggestions/" + first + "/accept", Map.of("approve", true));
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode acceptedBody = JSON.readTree(accepted.getBody());
        assertThat(acceptedBody.path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(acceptedBody.path("resolvedStoryStatus").asString()).isEqualTo("APPROVED");
        String storyId = acceptedBody.path("resolvedStoryId").asString();
        assertThat(get(OWNER_USER_ID, orgId, "/api/projects/" + projectId + "/stories/" + storyId).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        if (suggestions.size() > 1) {
            String second = suggestions.get(1).path("id").asString();
            ResponseEntity<String> dismissed = post(OWNER_USER_ID, orgId,
                    "/api/projects/" + projectId + "/suggestions/" + second + "/dismiss", null);
            assertThat(JSON.readTree(dismissed.getBody()).path("status").asString()).isEqualTo("DISMISSED");
        }

        // The listed reply shows the decision taken on its suggestions.
        JsonNode reply = JSON.readTree(get(OWNER_USER_ID, orgId, chat).getBody()).get(1);
        assertThat(reply.path("suggestions").get(0).path("status").asString()).isEqualTo("ACCEPTED");

        // A blank message is a bad request; a suggestion of another project is not found.
        assertThat(post(OWNER_USER_ID, orgId, chat, Map.of("content", "  ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/suggestions/" + UUID.randomUUID() + "/dismiss", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("accepting with approve needs STORY_APPROVE on top of SESSION_DECIDE; the response carries the story status")
    void accept_with_approve_requires_story_approve() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "tenant_" + slug;
        UUID orgId = createOrganization(suffix, slug);
        jdbcTemplate.update("UPDATE public.organizations SET member_base_permission = 'NONE' WHERE id = ?", orgId);
        UUID projectId = createProject(orgId, slug);
        createMember(orgId, Map.of("userId", MEMBER_USER_ID, "email", "decider@example.com",
                "displayName", "Decider", "role", "MEMBER"));
        createMember(orgId, Map.of("userId", APPROVER_USER_ID, "email", "approver@example.com",
                "displayName", "Approver", "role", "MEMBER"));
        assignRole(orgId, projectId, schema, "decider@example.com", "Decider",
                List.of("STORY_READ", "SESSION_READ", "SESSION_DECIDE"));
        assignRole(orgId, projectId, schema, "approver@example.com", "Approver",
                List.of("STORY_READ", "SESSION_READ", "SESSION_DECIDE", "STORY_APPROVE"));

        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JsonNode exchange = JSON.readTree(post(OWNER_USER_ID, orgId,
                    "/api/projects/" + projectId + "/assistant/messages",
                    Map.of("content", "Quiero que el usuario inicie sesión con su cuenta de Google " + i)).getBody());
            exchange.path("answer").path("suggestions").forEach(x -> ids.add(x.path("id").asString()));
        }
        assertThat(ids.size()).isGreaterThanOrEqualTo(2);
        String base = "/api/projects/" + projectId + "/suggestions/";

        // Without STORY_APPROVE, approving is forbidden and nothing changes: the suggestion stays pending.
        assertThat(post(MEMBER_USER_ID, orgId, base + ids.get(0) + "/accept", Map.of("approve", true))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(OWNER_USER_ID, orgId, "/api/projects/" + projectId + "/suggestions?status=PENDING&size=100").getBody()).contains(ids.get(0));

        // The same caller saves it as a draft (an empty body, like an older client): the story is DRAFT,
        // in the response and in the backlog.
        JsonNode draft = JSON.readTree(post(MEMBER_USER_ID, orgId, base + ids.get(0) + "/accept",
                Map.of()).getBody());
        assertThat(draft.path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(draft.path("resolvedStoryStatus").asString()).isEqualTo("DRAFT");
        assertThat(get(OWNER_USER_ID, orgId, "/api/projects/" + projectId + "/stories/"
                + draft.path("resolvedStoryId").asString()).getBody()).contains("\"status\":\"DRAFT\"");

        // With STORY_APPROVE the story is born APPROVED in the same call, and the feed reports it.
        JsonNode approved = JSON.readTree(post(APPROVER_USER_ID, orgId, base + ids.get(1) + "/accept",
                Map.of("approve", true)).getBody());
        assertThat(approved.path("resolvedStoryStatus").asString()).isEqualTo("APPROVED");
        assertThat(get(OWNER_USER_ID, orgId, "/api/projects/" + projectId + "/stories/"
                + approved.path("resolvedStoryId").asString()).getBody()).contains("\"status\":\"APPROVED\"");
        String accepted = get(OWNER_USER_ID, orgId, "/api/projects/" + projectId + "/suggestions?status=ACCEPTED&size=100").getBody();
        assertThat(accepted).contains("\"resolvedStoryStatus\":\"APPROVED\"")
                .contains("\"resolvedStoryStatus\":\"DRAFT\"");
    }

    // ----- helpers -----

    private void assignRole(UUID orgId, UUID projectId, String schema, String email, String roleName,
                            List<String> permissions) {
        assertThat(post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects/" + projectId + "/roles",
                Map.of("name", roleName, "permissions", permissions)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String roleId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".project_roles WHERE project_id = ?::uuid AND name = ?",
                String.class, projectId, roleName);
        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, email);
        assertThat(post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects/" + projectId + "/members",
                Map.of("memberId", memberId, "roleId", roleId)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> get(String userId, UUID orgId, String uri) {
        return client().get().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> post(String userId, UUID orgId, String uri, Map<String, Object> body) {
        var spec = client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1");
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
        assertThat(post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", body).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private UUID createProject(UUID orgId, String slug) {
        ResponseEntity<String> res = post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects", Map.of(
                "name", "Chat Project",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Hexagonal",
                "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Chat Project"));
    }
}
