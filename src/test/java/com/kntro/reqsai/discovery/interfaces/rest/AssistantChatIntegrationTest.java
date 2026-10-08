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
                "/api/projects/" + projectId + "/suggestions/" + first + "/accept", Map.of());
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode acceptedBody = JSON.readTree(accepted.getBody());
        assertThat(acceptedBody.path("status").asString()).isEqualTo("ACCEPTED");
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

    // ----- helpers -----

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
