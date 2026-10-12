package com.kntro.reqsai.workspace.interfaces.rest;

import com.kntro.reqsai.codebase.support.FakeGitHub;
import com.kntro.reqsai.gateway.StubJiraProviderConfig;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubEmbeddingConfig;
import com.kntro.reqsai.testsupport.StubRequirementGenerationConfig;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Creating a project with a Jira project and repositories, over HTTP and the database, against a fake GitHub and
 * the stubbed Jira: the project answers at once, its sources are set up in the background and followed through
 * {@code GET /api/projects/{id}/setup}; a bad source is refused before the project exists; a source that fails
 * later never takes the project with it, and can be skipped or retried.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({StubJiraProviderConfig.class, StubEmbeddingConfig.class, StubRequirementGenerationConfig.class})
@Tag("integration")
@DisplayName("Integration: project created with Jira and GitHub sources")
class ProjectSetupIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000009";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLIENT_ID = "Iv1.reqsai-test";
    private static final String CLIENT_SECRET = "client-secret-for-tests";
    private static final KeyPair APP_KEY = rsaKey();
    private static final FakeGitHub GITHUB = startGitHub();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static FakeGitHub startGitHub() {
        try {
            FakeGitHub github = new FakeGitHub();
            github.put("acme", "reservas", "main", false, null, FakeGitHub.restaurantApp());
            github.put("acme", "roto", "main", false, null, FakeGitHub.restaurantApp());
            github.app(CLIENT_ID, CLIENT_SECRET, APP_KEY.getPublic());
            return github;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static KeyPair rsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String appKeyAsSecret() {
        byte[] pkcs8 = APP_KEY.getPrivate().getEncoded();
        byte[] pkcs1 = java.util.Arrays.copyOfRange(pkcs8, 26, pkcs8.length);
        String pem = "-----BEGIN RSA PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pkcs1)
                + "\n-----END RSA PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
    }

    @DynamicPropertySource
    static void github(DynamicPropertyRegistry registry) {
        registry.add("reqsai.codebase.github.api-url", GITHUB::apiUrl);
        registry.add("reqsai.codebase.github.web-url", GITHUB::apiUrl);
        registry.add("reqsai.codebase.github.app.slug", () -> "reqsai-test");
        registry.add("reqsai.codebase.github.app.client-id", () -> CLIENT_ID);
        registry.add("reqsai.codebase.github.app.client-secret", () -> CLIENT_SECRET);
        registry.add("reqsai.codebase.github.app.private-key", ProjectSetupIntegrationTest::appKeyAsSecret);
        registry.add("reqsai.codebase.github.app.webhook-secret", () -> "webhook-secret-for-tests");
    }

    @AfterAll
    static void stopGitHub() {
        GITHUB.close();
    }

    @Test
    @DisplayName("opens at once, then links Jira, imports its stories and indexes the repository in the background")
    void setsUpEverything() throws Exception {
        UUID orgId = createOrganization();
        String connectionId = connectJira(orgId);

        ResponseEntity<String> created = createProject(orgId, "Todo conectado", Map.of(
                "jira", Map.of("connectionId", connectionId, "projectKey", "PAY", "issueTypeName", "Story",
                        "importStories", true),
                "github", List.of(Map.of("repository", "acme/reservas"))));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID projectId = UUID.fromString(JSON.readTree(created.getBody()).path("id").asString());

        JsonNode setup = awaitSetupIdle(orgId, projectId);
        assertThat(statuses(setup)).containsExactly("JIRA_LINK=DONE", "JIRA_IMPORT=DONE", "GITHUB_REPOS=DONE");
        assertThat(setup.path("steps").get(2).path("total").asInt()).isPositive();
        assertThat(texts(setup.path("detectedStack").path("languages"))).contains("TypeScript");

        ResponseEntity<String> target = send(HttpMethod.GET, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/integration/jira/target", null);
        assertThat(target.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(target.getBody()).path("jiraProjectKey").asString()).isEqualTo("PAY");
        JsonNode repos = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/code/repositories", null).getBody());
        assertThat(repos).hasSize(1);
        assertThat(repos.get(0).path("status").asString()).isEqualTo("READY");
    }

    @Test
    @DisplayName("a project without sources has an empty setup")
    void noSources() throws Exception {
        UUID orgId = createOrganization();
        ResponseEntity<String> created = createProject(orgId, "Sin fuentes", null);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID projectId = UUID.fromString(JSON.readTree(created.getBody()).path("id").asString());

        JsonNode setup = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/setup", null).getBody());
        assertThat(setup.path("steps")).isEmpty();
        assertThat(setup.path("active").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("refuses what cannot work before creating the project")
    void refusesBadSourcesUpFront() throws Exception {
        UUID orgId = createOrganization();
        int before = projectCount(orgId);

        ResponseEntity<String> noJira = createProject(orgId, "Sin Jira", Map.of(
                "jira", Map.of("projectKey", "PAY", "issueTypeName", "Story", "importStories", false)));
        assertThat(noJira.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(code(noJira)).isEqualTo("INTEGRATION_CONNECTION_NOT_FOUND");

        ResponseEntity<String> unknownConnection = createProject(orgId, "Conexión ajena", Map.of(
                "jira", Map.of("connectionId", UUID.randomUUID().toString(), "projectKey", "PAY",
                        "issueTypeName", "Story", "importStories", false)));
        assertThat(code(unknownConnection)).isEqualTo("INTEGRATION_CONNECTION_NOT_FOUND");

        ResponseEntity<String> unknownRepo = createProject(orgId, "Repo inexistente", Map.of(
                "github", List.of(Map.of("repository", "acme/no-existe"))));
        assertThat(code(unknownRepo)).isEqualTo("CODE_REPOSITORY_NOT_FOUND");

        ResponseEntity<String> tooMany = createProject(orgId, "Demasiados", Map.of(
                "github", List.of(Map.of("repository", "acme/reservas"), Map.of("repository", "acme/roto"),
                        Map.of("repository", "acme/a"), Map.of("repository", "acme/b"))));
        assertThat(code(tooMany)).isEqualTo("CODE_REPOSITORY_LIMIT_REACHED");

        ResponseEntity<String> twice = createProject(orgId, "Repetido", Map.of(
                "github", List.of(Map.of("repository", "acme/reservas"), Map.of("repository", "ACME/Reservas"))));
        assertThat(code(twice)).isEqualTo("CODE_REPOSITORY_ALREADY_CONNECTED");

        assertThat(projectCount(orgId)).isEqualTo(before);
    }

    @Test
    @DisplayName("a source that fails never takes the project down: it can be retried, and another skipped")
    void failureKeepsTheProject() throws Exception {
        UUID orgId = createOrganization();
        GITHUB.breakDownload("acme", "roto", true);
        try {
            ResponseEntity<String> created = createProject(orgId, "Con fallo", Map.of(
                    "github", List.of(Map.of("repository", "acme/roto"))));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID projectId = UUID.fromString(JSON.readTree(created.getBody()).path("id").asString());

            JsonNode failed = awaitSetupIdle(orgId, projectId);
            assertThat(statuses(failed)).containsExactly("GITHUB_REPOS=FAILED");
            assertThat(send(HttpMethod.GET, OWNER_USER_ID, orgId,
                    "/api/organizations/" + orgId + "/projects/" + projectId, null).getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId,
                    "/api/projects/" + projectId + "/setup/steps/JIRA_LINK/retry", null).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);

            GITHUB.breakDownload("acme", "roto", false);
            ResponseEntity<String> retried = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                    "/api/projects/" + projectId + "/setup/steps/GITHUB_REPOS/retry", null);
            assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(statuses(awaitSetupIdle(orgId, projectId))).containsExactly("GITHUB_REPOS=DONE");
            assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, orgId,
                    "/api/projects/" + projectId + "/setup/steps/GITHUB_REPOS/retry", null)))
                    .isEqualTo("PROJECT_SETUP_STEP_NOT_RETRIABLE");

            GITHUB.breakDownload("acme", "roto", true);
            UUID other = UUID.fromString(JSON.readTree(createProject(orgId, "Para omitir", Map.of(
                    "github", List.of(Map.of("repository", "acme/roto")))).getBody()).path("id").asString());
            awaitSetupIdle(orgId, other);
            ResponseEntity<String> skipped = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                    "/api/projects/" + other + "/setup/steps/GITHUB_REPOS/skip", null);
            assertThat(skipped.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(statuses(JSON.readTree(skipped.getBody()))).containsExactly("GITHUB_REPOS=SKIPPED");
        } finally {
            GITHUB.breakDownload("acme", "roto", false);
        }
    }

    @Test
    @DisplayName("only who can read integrations sees the setup")
    void permissions() throws Exception {
        UUID orgId = createOrganization();
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID projectId = UUID.fromString(JSON.readTree(createProject(orgId, "Privado", null).getBody())
                .path("id").asString());

        assertThat(send(HttpMethod.GET, MEMBER_USER_ID, orgId, "/api/projects/" + projectId + "/setup", null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, MEMBER_USER_ID, orgId,
                "/api/projects/" + projectId + "/setup/steps/GITHUB_REPOS/skip", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- helpers -----

    private JsonNode awaitSetupIdle(UUID orgId, UUID projectId) throws Exception {
        JsonNode last = null;
        for (int i = 0; i < 300; i++) {
            ResponseEntity<String> response = send(HttpMethod.GET, OWNER_USER_ID, orgId,
                    "/api/projects/" + projectId + "/setup", null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            last = JSON.readTree(response.getBody());
            if (!last.path("steps").isEmpty() && !last.path("active").asBoolean()) return last;
            Thread.sleep(200);
        }
        throw new AssertionError("The setup never settled: " + last);
    }

    private static List<String> statuses(JsonNode setup) {
        List<String> out = new java.util.ArrayList<>();
        setup.path("steps").forEach(step -> out.add(step.path("key").asString() + "=" + step.path("status").asString()));
        return out;
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static String code(ResponseEntity<String> response) {
        return JSON.readTree(response.getBody()).path("code").asString();
    }

    private int projectCount(UUID orgId) {
        String slug = jdbcTemplate.queryForObject("SELECT slug FROM public.organizations WHERE id = ?::uuid",
                String.class, orgId.toString());
        return jdbcTemplate.queryForObject("SELECT count(*) FROM \"tenant_" + slug + "\".projects", Integer.class);
    }

    private ResponseEntity<String> createProject(UUID orgId, String name, @Nullable Map<String, Object> sources) {
        Map<String, Object> body = new HashMap<>(Map.of("name", name));
        if (sources != null) body.put("sources", sources);
        return send(HttpMethod.POST, OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects", body);
    }

    private String connectJira(UUID orgId) {
        ResponseEntity<String> res = send(HttpMethod.POST, OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/integrations/jira",
                Map.of("siteUrl", "https://acme.atlassian.net", "email", "pm@acme.com", "apiToken", "tok"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JSON.readTree(res.getBody()).path("id").asString();
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

    private UUID createOrganization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, "acme-" + suffix));
    }
}
