package com.kntro.reqsai.codebase.interfaces.rest;

import com.kntro.reqsai.codebase.support.FakeGitHub;
import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.StoryInsight;
import com.kntro.reqsai.discovery.domain.model.CodeFinding;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The code-aware copilot end to end over HTTP and the database, against a fake GitHub: the client's repository
 * is connected and indexed (profile, modules, no secrets, no raw code), the copilot reads it when the analyst
 * asks for a requirement (the suggestion comes back flagged against the code, with the module), the accepted
 * story keeps the code reference, and reindexing and disconnecting work. Without an AI model the modules get
 * structural descriptions; the model is a stub that flags a conflict when the prompt carries code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(CodeCopilotIntegrationTest.CodeAwareGenerationStub.class)
@Tag("integration")
@DisplayName("Integration: code-aware copilot")
class CodeCopilotIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000009";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final FakeGitHub GITHUB = startGitHub();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static FakeGitHub startGitHub() {
        try {
            FakeGitHub github = new FakeGitHub();
            github.put("acme", "reservas", "main", false, "", FakeGitHub.restaurantApp());
            github.put("acme", "interno", "main", true, "read-only-token", Map.of(
                    "src/a/one.ts", "export const ONE = 1;", "src/a/two.ts", "export const TWO = 2;"));
            return github;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void github(DynamicPropertyRegistry registry) {
        registry.add("reqsai.codebase.github.api-url", GITHUB::apiUrl);
    }

    @AfterAll
    static void stopGitHub() {
        GITHUB.close();
    }

    @Test
    @DisplayName("connects and indexes a repository, and the copilot flags a requirement against its code")
    void indexAndUse() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        createMember(orgId, Map.of("userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Member", "role", "MEMBER"));
        String repos = "/api/projects/" + projectId + "/code/repositories";

        // Validation and permissions are answered before anything is stored.
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, repos, Map.of("repository", "gitlab.com/acme/x"))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, orgId, repos, Map.of("repository", "acme/no-existe"))))
                .isEqualTo("CODE_REPOSITORY_NOT_FOUND");
        assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, orgId, repos, Map.of("repository", "acme/interno"))))
                .isEqualTo("CODE_REPOSITORY_NOT_FOUND");
        assertThat(send(HttpMethod.POST, MEMBER_USER_ID, orgId, repos, Map.of("repository", "acme/reservas"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Connect the public app; it is indexed in the background.
        ResponseEntity<String> connected = send(HttpMethod.POST, OWNER_USER_ID, orgId, repos,
                Map.of("repository", "https://github.com/acme/reservas"));
        assertThat(connected.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode repo = JSON.readTree(connected.getBody());
        String repoId = repo.path("id").asString();
        assertThat(repo.path("fullName").asString()).isEqualTo("acme/reservas");
        assertThat(repo.path("branch").asString()).isEqualTo("main");
        assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, orgId, repos, Map.of("repository", "ACME/Reservas"))))
                .isEqualTo("CODE_REPOSITORY_ALREADY_CONNECTED");

        JsonNode ready = awaitStatus(orgId, repos, repoId, "READY");
        assertThat(ready.path("commitSha").asString()).isNotBlank();
        assertThat(ready.path("summarized").asBoolean()).isFalse();
        assertThat(ready.path("moduleCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(ready.path("modulesDone").asInt()).isEqualTo(ready.path("moduleCount").asInt());
        assertThat(texts(ready.path("profile").path("languages"))).contains("TypeScript");
        assertThat(texts(ready.path("profile").path("frameworks"))).contains("Express");
        assertThat(texts(ready.path("profile").path("databases"))).contains("PostgreSQL");
        assertThat(ready.path("profile").path("overview").asString()).contains("reservas en línea");

        // The module map (INTEGRATION_READ, which a read-only member lacks): endpoints and entities, no secrets
        // and no raw code.
        assertThat(send(HttpMethod.GET, MEMBER_USER_ID, orgId, repos + "/" + repoId + "/modules", null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> modulesResponse = send(HttpMethod.GET, OWNER_USER_ID, orgId,
                repos + "/" + repoId + "/modules", null);
        assertThat(modulesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(modulesResponse.getBody()).doesNotContain("sk_live", "SuperSecret123", "getTime()");
        JsonNode modules = JSON.readTree(modulesResponse.getBody());
        JsonNode reservations = find(modules, "path", "src/reservations");
        assertThat(texts(reservations.path("endpoints"))).contains("POST /reservations", "DELETE /reservations/:id");
        assertThat(reservations.path("url").asString()).contains("/acme/reservas/tree/").endsWith("/src/reservations");
        assertThat(texts(find(modules, "path", "db/migrations").path("entities"))).contains("reservations", "payments");

        // The analyst types a requirement: the copilot reads the code and flags the conflict with the module.
        ResponseEntity<String> sent = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/assistant/messages",
                Map.of("content", "Quiero que el comensal pueda cancelar su reserva hasta 24 horas antes"));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode suggestion = JSON.readTree(sent.getBody()).path("answer").path("suggestions").get(0);
        assertThat(suggestion.path("code").path("finding").asString()).isEqualTo("CONFLICTS_WITH_CODE");
        assertThat(suggestion.path("code").path("note").asString()).contains("2 h");
        JsonNode reference = suggestion.path("code").path("references").get(0);
        assertThat(reference.path("repository").asString()).isEqualTo("acme/reservas");
        assertThat(reference.path("path").asString()).isEqualTo("src/reservations");
        assertThat(suggestion.path("evidence").path("quote").asString()).contains("24 horas");

        // Accepted, the story keeps the module it relates to and where it was said.
        ResponseEntity<String> accepted = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/suggestions/" + suggestion.path("id").asString() + "/accept", Map.of());
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        String storyId = JSON.readTree(accepted.getBody()).path("resolvedStoryId").asString();
        JsonNode story = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/stories/" + storyId, null).getBody());
        assertThat(story.path("codeReferences").get(0).path("path").asString()).isEqualTo("src/reservations");
        assertThat(story.path("origin").path("quote").asString()).contains("24 horas");

        // Reindex reads the branch again; unchanged modules keep their description.
        int downloads = GITHUB.zipDownloads();
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, repos + "/" + repoId + "/reindex", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        awaitStatus(orgId, repos, repoId, "READY");
        assertThat(GITHUB.zipDownloads()).isGreaterThan(downloads);

        // A private repository needs its read-only token, which is stored but never returned.
        ResponseEntity<String> privateRepo = send(HttpMethod.POST, OWNER_USER_ID, orgId, repos,
                Map.of("repository", "acme/interno", "accessToken", "read-only-token"));
        assertThat(privateRepo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(privateRepo.getBody()).doesNotContain("read-only-token");
        JsonNode privateJson = JSON.readTree(privateRepo.getBody());
        assertThat(privateJson.path("private").asBoolean()).isTrue();
        assertThat(privateJson.path("hasToken").asBoolean()).isTrue();
        awaitStatus(orgId, repos, privateJson.path("id").asString(), "READY");
        byte[] stored = jdbcTemplate.queryForObject("SELECT access_token_ciphertext FROM \"tenant_" + slug
                + "\".code_repositories WHERE id = ?::uuid", byte[].class, privateJson.path("id").asString());
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("read-only-token");

        // Disconnecting forgets the repository and its modules.
        assertThat(send(HttpMethod.DELETE, OWNER_USER_ID, orgId, repos + "/" + repoId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, repos, null).getBody()).size()).isEqualTo(1);
        Integer left = jdbcTemplate.queryForObject("SELECT count(*) FROM \"tenant_" + slug
                + "\".code_modules WHERE repository_id = ?::uuid", Integer.class, repoId);
        assertThat(left).isZero();
    }

    /**
     * The model for this test: a requirement typed in the chat comes back as one story; when the prompt carries
     * the client's code it is flagged against the first module, as the real model is asked to.
     */
    @TestConfiguration
    static class CodeAwareGenerationStub {

        @Bean
        @Primary
        RequirementGenerationPort codeAwareGeneration() {
            return new RequirementGenerationPort() {
                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public GenerationResult generate(String transcript, String language) {
                    return generate(transcript, language, null);
                }

                @Override
                public GenerationResult generate(String transcript, String language,
                                                 @Nullable GenerationContext context) {
                    StoryInsight insight = StoryInsight.evidence(transcript);
                    if (context != null && context.code() != null && !context.code().modules().isEmpty()) {
                        GenerationContext.CodeModuleEntry module = context.code().modules().getFirst();
                        insight = new StoryInsight(transcript, CodeFinding.CONFLICTS_WITH_CODE,
                                "El código permite cancelar hasta 2 h antes; el cliente pide 24 h",
                                List.of(new CodeReference(module.repository(), module.path(), module.name(),
                                        module.url())));
                    }
                    return new GenerationResult(List.of(new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                            "Cancelar reserva hasta 24 horas antes", "comensal",
                            "cancelar mi reserva hasta 24 horas antes", "no pagar por una mesa que no usaré",
                            Priority.HIGH, 3, List.of(), null, null, insight)));
                }
            };
        }
    }

    // ----- helpers -----

    private JsonNode awaitStatus(UUID orgId, String repos, String repoId, String status) throws InterruptedException {
        JsonNode last = null;
        for (int i = 0; i < 150; i++) {
            JsonNode list = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, repos, null).getBody());
            last = find(list, "id", repoId);
            if (status.equals(last.path("status").asString())) return last;
            if ("FAILED".equals(last.path("status").asString())) {
                throw new AssertionError("Indexing failed: " + last.path("error").asString());
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Repository never reached " + status + ": " + last);
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asString())) return node;
        }
        throw new AssertionError("No element with " + field + "=" + value + " in " + array);
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static String code(ResponseEntity<String> response) {
        return JSON.readTree(response.getBody()).path("code").asString();
    }

    private ResponseEntity<String> send(HttpMethod method, String userId, UUID orgId, String uri,
                                        @Nullable Map<String, Object> body) {
        var spec = client().method(method).uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1");
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(new HashMap<>(body));
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

    private UUID createProject(UUID orgId, String slug) {
        ResponseEntity<String> res = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/projects", Map.of(
                        "name", "Code Project",
                        "programmingLanguages", List.of("TypeScript"),
                        "frameworks", List.of("Express"),
                        "clientPlatforms", List.of("Web"),
                        "databases", List.of("PostgreSQL"),
                        "architecture", "Hexagonal",
                        "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Code Project"));
    }
}
