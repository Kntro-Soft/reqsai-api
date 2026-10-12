package com.kntro.reqsai.codebase.interfaces.rest;

import com.kntro.reqsai.codebase.api.CodeSnippet;
import com.kntro.reqsai.codebase.support.FakeGitHub;
import com.kntro.reqsai.discovery.application.port.AssistantReply;
import com.kntro.reqsai.discovery.application.port.BacklogOverview;
import com.kntro.reqsai.discovery.application.port.ChatTurn;
import com.kntro.reqsai.discovery.application.port.CodeVerification;
import com.kntro.reqsai.discovery.application.port.CodeVerificationPort;
import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.StoryInsight;
import com.kntro.reqsai.discovery.domain.model.CodeFinding;
import com.kntro.reqsai.discovery.domain.event.SuggestionCodeVerifiedEvent;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;
import com.kntro.reqsai.discovery.domain.model.CodeVerifyState;
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
import org.springframework.context.event.EventListener;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingVerifier verifier;

    @Autowired
    private VerificationEvents events;

    private static final String CLIENT_ID = "Iv1.reqsai-test";
    private static final String CLIENT_SECRET = "client-secret-for-tests";
    private static final String WEBHOOK_SECRET = "webhook-secret-for-tests";
    private static final long INSTALLATION = 1001L;
    private static final long OTHER_INSTALLATION = 2002L;
    private static final KeyPair APP_KEY = rsaKey();
    private static final FakeGitHub GITHUB = startGitHub();

    private static FakeGitHub startGitHub() {
        try {
            FakeGitHub github = new FakeGitHub();
            github.put("acme", "reservas", "main", false, null, FakeGitHub.restaurantApp());
            github.put("acme", "interno", "main", true, INSTALLATION, Map.of(
                    "src/a/one.ts", "export const ONE = 1;", "src/a/two.ts", "export const TWO = 2;"));
            github.put("acme", "menu", "main", false, INSTALLATION, Map.of(
                    "src/menu/dishes.ts", "export const DISHES = [];", "src/menu/prices.ts", "export const IGV = 18;"));
            github.app(CLIENT_ID, CLIENT_SECRET, APP_KEY.getPublic());
            github.installation(INSTALLATION, "acme", false);
            github.installation(OTHER_INSTALLATION, "otra", false);
            github.userCode("code-acme", INSTALLATION);
            github.userCode("code-otra", OTHER_INSTALLATION);
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

    /** The App's key as GitHub hands it out (PKCS#1 PEM), passed base64-encoded like a one-line secret. */
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
        registry.add("reqsai.codebase.github.app.private-key", CodeCopilotIntegrationTest::appKeyAsSecret);
        registry.add("reqsai.codebase.github.app.webhook-secret", () -> WEBHOOK_SECRET);
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
                "/api/projects/" + projectId + "/suggestions/" + suggestion.path("id").asString() + "/accept", Map.of("approve", true));
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode acceptedBody = JSON.readTree(accepted.getBody());
        assertThat(acceptedBody.path("resolvedStoryStatus").asString()).isEqualTo("APPROVED");
        String storyId = acceptedBody.path("resolvedStoryId").asString();
        JsonNode story = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/stories/" + storyId, null).getBody());
        assertThat(story.path("status").asString()).isEqualTo("APPROVED");
        assertThat(story.path("codeReferences").get(0).path("path").asString()).isEqualTo("src/reservations");
        assertThat(story.path("origin").path("quote").asString()).contains("24 horas");

        // Reindex reads the branch again; unchanged modules keep their description.
        int downloads = GITHUB.zipDownloads();
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, repos + "/" + repoId + "/reindex", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        awaitStatus(orgId, repos, repoId, "READY");
        assertThat(GITHUB.zipDownloads()).isGreaterThan(downloads);

        assertThat(repo.path("source").asString()).isEqualTo("PUBLIC");
        assertThat(repo.path("autoUpdate").asBoolean()).isFalse();

        // Disconnecting forgets the repository and its modules.
        assertThat(send(HttpMethod.DELETE, OWNER_USER_ID, orgId, repos + "/" + repoId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, repos, null).getBody()).size()).isZero();
        Integer left = jdbcTemplate.queryForObject("SELECT count(*) FROM \"tenant_" + slug
                + "\".code_modules WHERE repository_id = ?::uuid", Integer.class, repoId);
        assertThat(left).isZero();
    }

    @Test
    @DisplayName("GitHub App: install, pick a private repository, update on push, stop when GitHub stops sharing")
    void githubApp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        createMember(orgId, Map.of("userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Member", "role", "MEMBER"));
        String github = "/api/organizations/" + orgId + "/code/github";
        String repos = "/api/projects/" + projectId + "/code/repositories";

        // The server has the App; the organization has not installed it yet. Only owners and admins install it.
        JsonNode connection = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, github, null).getBody());
        assertThat(connection.path("available").asBoolean()).isTrue();
        assertThat(connection.path("installations").size()).isZero();
        assertThat(send(HttpMethod.POST, MEMBER_USER_ID, orgId, github + "/install", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Start: GitHub's install page, with a signed state for this organization and user.
        String url = JSON.readTree(send(HttpMethod.POST, OWNER_USER_ID, orgId, github + "/install", null).getBody())
                .path("url").asString();
        assertThat(url).startsWith(GITHUB.apiUrl() + "/apps/reqsai-test/installations/new?state=");
        String state = URLDecoder.decode(url.substring(url.indexOf("state=") + 6), StandardCharsets.UTF_8);

        // The redirect is checked: a tampered state, or an installation the GitHub user cannot access, is refused.
        assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, orgId, github + "/installations",
                install(INSTALLATION, state + "x", "code-acme")))).isEqualTo("CODE_HOST_INSTALL_STATE_INVALID");
        assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, orgId, github + "/installations",
                install(INSTALLATION, state, "code-otra")))).isEqualTo("CODE_HOST_INSTALLATION_FORBIDDEN");
        JsonNode requested = JSON.readTree(send(HttpMethod.POST, OWNER_USER_ID, orgId, github + "/installations",
                Map.of("setupAction", "request", "state", state)).getBody());
        assertThat(requested.path("status").asString()).isEqualTo("REQUESTED");

        // Linked: the account and where to manage it on GitHub; no token is kept.
        ResponseEntity<String> linked = send(HttpMethod.POST, OWNER_USER_ID, orgId, github + "/installations",
                install(INSTALLATION, state, "code-acme"));
        assertThat(linked.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode installation = JSON.readTree(linked.getBody()).path("installation");
        assertThat(JSON.readTree(linked.getBody()).path("status").asString()).isEqualTo("LINKED");
        assertThat(installation.path("account").asString()).isEqualTo("acme");
        assertThat(installation.path("manageUrl").asString()).endsWith("/settings/installations/" + INSTALLATION);
        assertThat(linked.getBody()).doesNotContain("ghs_", "ghu_");

        // The project picks from what the installation shares (a read-only member cannot list them).
        String picker = "/api/projects/" + projectId + "/code/github/repositories";
        assertThat(send(HttpMethod.GET, MEMBER_USER_ID, orgId, picker, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode available = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, picker, null).getBody());
        assertThat(texts(available, "fullName")).containsExactlyInAnyOrder("acme/interno", "acme/menu");
        assertThat(find(available, "fullName", "acme/interno").path("private").asBoolean()).isTrue();

        // A private repository through the App: indexed with installation tokens, updating on every push.
        ResponseEntity<String> privateRepo = send(HttpMethod.POST, OWNER_USER_ID, orgId, repos,
                Map.of("repository", "acme/interno", "installationId", INSTALLATION));
        assertThat(privateRepo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode interno = JSON.readTree(privateRepo.getBody());
        assertThat(interno.path("private").asBoolean()).isTrue();
        assertThat(interno.path("source").asString()).isEqualTo("GITHUB_APP");
        assertThat(interno.path("autoUpdate").asBoolean()).isTrue();
        String internoId = interno.path("id").asString();
        JsonNode first = awaitStatus(orgId, repos, internoId, "READY");
        assertThat(GITHUB.tokensMinted()).isPositive();

        // A typed repository the installation shares is read through it too; a shared one already connected
        // shows in the picker.
        JsonNode menu = JSON.readTree(send(HttpMethod.POST, OWNER_USER_ID, orgId, repos,
                Map.of("repository", "acme/menu")).getBody());
        assertThat(menu.path("source").asString()).isEqualTo("GITHUB_APP");
        awaitStatus(orgId, repos, menu.path("id").asString(), "READY");
        assertThat(find(JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, picker, null).getBody()),
                "fullName", "acme/interno").path("connected").asBoolean()).isTrue();

        // A push to main: GitHub's signed webhook reindexes at the new commit. A bad signature is refused.
        Map<String, String> changed = new LinkedHashMap<>(GITHUB.repo("acme", "interno").files());
        changed.put("src/b/three.ts", "export const THREE = 3;");
        changed.put("src/b/four.ts", "export const FOUR = 4;");
        GITHUB.put("acme", "interno", "main", true, INSTALLATION, changed);
        String sha = GITHUB.repo("acme", "interno").sha();
        assertThat(sha).isNotEqualTo(first.path("commitSha").asString());
        String push = """
                {"ref":"refs/heads/main","after":"%s","deleted":false,
                 "repository":{"name":"interno","owner":{"login":"acme","name":"acme"}},"installation":{"id":%d}}
                """.formatted(sha, INSTALLATION);
        assertThat(webhook("push", push, "sha256=" + "0".repeat(64)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(webhook("push", push, sign(push)).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode updated = awaitCommit(orgId, repos, internoId, sha);
        assertThat(updated.path("moduleCount").asInt()).isGreaterThan(first.path("moduleCount").asInt());

        // Another organization cannot read through this installation.
        String otherSuffix = UUID.randomUUID().toString().substring(0, 8);
        UUID otherOrg = createOrganization(otherSuffix, "acme-" + otherSuffix);
        UUID otherProject = createProject(otherOrg, "acme-" + otherSuffix);
        assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, otherOrg, "/api/projects/" + otherProject
                + "/code/repositories", Map.of("repository", "acme/interno", "installationId", INSTALLATION))))
                .isEqualTo("CODE_HOST_INSTALLATION_NOT_FOUND");

        // GitHub stops sharing the repository: it stops updating and says why.
        String removed = """
                {"action":"removed","installation":{"id":%d},
                 "repositories_removed":[{"full_name":"acme/interno"}],"repositories_added":[]}
                """.formatted(INSTALLATION);
        assertThat(webhook("installation_repositories", removed, sign(removed)).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        JsonNode revoked = awaitStatus(orgId, repos, internoId, "FAILED", true);
        assertThat(revoked.path("error").asString()).contains("ya no está compartido");
        assertThat(revoked.path("autoUpdate").asBoolean()).isFalse();

        // Disconnecting GitHub unlinks the installation; the menu repository stops updating.
        assertThat(send(HttpMethod.DELETE, OWNER_USER_ID, orgId, github + "/installations/" + INSTALLATION, null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, github, null).getBody())
                .path("installations").size()).isZero();
        assertThat(awaitStatus(orgId, repos, menu.path("id").asString(), "FAILED", true).path("error").asString())
                .contains("Se desconectó GitHub");

        // GitHub connected again: retrying reads the repository through the installation again.
        String again = JSON.readTree(send(HttpMethod.POST, OWNER_USER_ID, orgId, github + "/install", null).getBody())
                .path("url").asString();
        String againState = URLDecoder.decode(again.substring(again.indexOf("state=") + 6), StandardCharsets.UTF_8);
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, github + "/installations",
                install(INSTALLATION, againState, "code-acme")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode retried = JSON.readTree(send(HttpMethod.POST, OWNER_USER_ID, orgId,
                repos + "/" + menu.path("id").asString() + "/reindex", null).getBody());
        assertThat(retried.path("source").asString()).isEqualTo("GITHUB_APP");
        assertThat(awaitStatus(orgId, repos, menu.path("id").asString(), "READY").path("autoUpdate").asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("deep verification: searches the pure code, updates the card, fails and retries, and answers chat questions")
    void deepVerification() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        createMember(orgId, Map.of("userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Member", "role", "MEMBER"));
        String messages = "/api/projects/" + projectId + "/assistant/messages";
        int callsBefore = verifier.calls.get();

        // Without a connected repository there is nothing to verify against: 409, and the chat keeps its own reply.
        String cancelled = chatSuggestionId(orgId, messages, "Quiero que el comensal pueda cancelar su reserva hasta 24 horas antes");
        assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, orgId, verifyPath(projectId, cancelled), null)))
                .isEqualTo("CODE_REPOSITORY_NOT_READY");
        JsonNode noRepoChat = JSON.readTree(send(HttpMethod.POST, OWNER_USER_ID, orgId, messages,
                Map.of("content", "¿El código ya permite cancelar una reserva?")).getBody()).path("answer");
        assertThat(noRepoChat.path("codeSearched").asBoolean()).isFalse();
        assertThat(noRepoChat.path("content").asString()).isEqualTo("Busco en el código.");

        // Connect and index the public repository.
        String repos = "/api/projects/" + projectId + "/code/repositories";
        String repoId = JSON.readTree(send(HttpMethod.POST, OWNER_USER_ID, orgId, repos,
                Map.of("repository", "acme/reservas")).getBody()).path("id").asString();
        String sha = awaitStatus(orgId, repos, repoId, "READY").path("commitSha").asString();

        // Only someone who can decide suggestions and read integrations may ask; an unknown suggestion is a 404.
        assertThat(send(HttpMethod.POST, MEMBER_USER_ID, orgId, verifyPath(projectId, cancelled), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, verifyPath(projectId, UUID.randomUUID().toString()), null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 202 right away, the card in VERIFYING; the verdict arrives in the background.
        ResponseEntity<String> started = send(HttpMethod.POST, OWNER_USER_ID, orgId, verifyPath(projectId, cancelled), null);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(JSON.readTree(started.getBody()).path("code").path("verifyState").asString()).isEqualTo("VERIFYING");
        JsonNode verified = awaitVerification(orgId, projectId, cancelled, "VERIFIED");
        JsonNode codeInfo = verified.path("code");
        assertThat(codeInfo.path("verdict").asString()).isEqualTo("CONFLICTS_WITH_CODE");
        assertThat(codeInfo.path("finding").asString()).isEqualTo("CONFLICTS_WITH_CODE");
        assertThat(codeInfo.path("note").asString()).contains("2 h");
        assertThat(codeInfo.path("verifiedAt").asString()).isNotBlank();
        JsonNode reference = codeInfo.path("references").get(0);
        assertThat(reference.path("file").asString()).isEqualTo("src/reservations/cancellation.policy.ts");
        assertThat(reference.path("lineStart").asInt()).isEqualTo(2);
        assertThat(reference.path("lineEnd").asInt()).isEqualTo(2);
        assertThat(reference.path("quote").asString()).contains("CANCELLATION_LIMIT_HOURS = 2");
        assertThat(reference.path("url").asString())
                .contains("/acme/reservas/blob/" + sha + "/src/reservations/cancellation.policy.ts#L2");
        assertThat(verifier.calls.get() - callsBefore).isEqualTo(1);
        assertThat(events.statesOf(cancelled)).containsExactly(CodeVerifyState.VERIFYING, CodeVerifyState.VERIFIED);
        // No source code was persisted anywhere with the verdict.
        assertThat(verified.toString()).doesNotContain("getTime()");

        // Something the code does not have: nothing in the repository relates, so the model is not even called.
        String crypto = chatSuggestionId(orgId, messages, "Quiero pagar con blockchain y una wallet de criptomonedas");
        int callsBeforeCrypto = verifier.calls.get();
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, verifyPath(projectId, crypto), null).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        JsonNode notFound = awaitVerification(orgId, projectId, crypto, "VERIFIED").path("code");
        assertThat(notFound.path("verdict").asString()).isEqualTo("NOT_FOUND");
        assertThat(notFound.path("finding").isNull() || notFound.path("finding").isMissingNode()).isTrue();
        assertThat(notFound.path("references").size()).isZero();
        assertThat(verifier.calls.get()).isEqualTo(callsBeforeCrypto);

        // A model failure ends as FAILED with a reason, and asking again retries.
        String failing = chatSuggestionId(orgId, messages, "Quiero cancelar la reserva [[fail]] hasta 24 horas antes");
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, verifyPath(projectId, failing), null).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        JsonNode failed = awaitVerification(orgId, projectId, failing, "FAILED").path("code");
        assertThat(failed.path("verifyError").asString()).isNotBlank().doesNotContain("quota exceeded");
        assertThat(events.statesOf(failing)).containsExactly(CodeVerifyState.VERIFYING, CodeVerifyState.FAILED);
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, verifyPath(projectId, failing), null).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        awaitVerification(orgId, projectId, failing, "FAILED");

        // A resolved suggestion cannot be verified.
        assertThat(send(HttpMethod.POST, OWNER_USER_ID, orgId, "/api/projects/" + projectId + "/suggestions/"
                + crypto + "/dismiss", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(code(send(HttpMethod.POST, OWNER_USER_ID, orgId, verifyPath(projectId, crypto), null)))
                .isEqualTo("SUGGESTION_ALREADY_RESOLVED");

        // The chat: the index could not answer, so the pure code is searched and the reply cites file and lines.
        int callsBeforeChat = verifier.calls.get();
        JsonNode answer = JSON.readTree(send(HttpMethod.POST, OWNER_USER_ID, orgId, messages,
                Map.of("content", "¿El código del sistema ya permite cancelar una reserva?")).getBody()).path("answer");
        assertThat(answer.path("codeSearched").asBoolean()).isTrue();
        assertThat(answer.path("content").asString()).contains("2 horas antes").contains("código fuente");
        assertThat(answer.path("codeReferences").get(0).path("file").asString())
                .isEqualTo("src/reservations/cancellation.policy.ts");
        assertThat(answer.path("codeReferences").get(0).path("url").asString())
                .contains("/blob/" + sha + "/src/reservations/cancellation.policy.ts#L2");
        assertThat(verifier.calls.get() - callsBeforeChat).isEqualTo(1);
        // The snippets the model read had their secrets removed.
        assertThat(verifier.lastSnippets).isNotEmpty();
        assertThat(verifier.lastSnippets.toString()).doesNotContain("sk_" + "live_");

        // The stored history keeps the references of that reply.
        JsonNode history = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, messages, null).getBody());
        JsonNode stored = find(history.isArray() ? history : history.path("content"), "id", answer.path("id").asString());
        assertThat(stored.path("codeSearched").asBoolean()).isTrue();
        assertThat(stored.path("codeReferences").size()).isEqualTo(1);
    }

    private String verifyPath(UUID projectId, String suggestionId) {
        return "/api/projects/" + projectId + "/suggestions/" + suggestionId + "/verify-code";
    }

    /** Types a requirement in the chat and returns the id of the suggestion it raised. */
    private String chatSuggestionId(UUID orgId, String messagesPath, String text) {
        ResponseEntity<String> sent = send(HttpMethod.POST, OWNER_USER_ID, orgId, messagesPath, Map.of("content", text));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JSON.readTree(sent.getBody()).path("answer").path("suggestions").get(0).path("id").asString();
    }

    private JsonNode awaitVerification(UUID orgId, UUID projectId, String suggestionId, String state)
            throws InterruptedException {
        JsonNode last = null;
        for (int i = 0; i < 150; i++) {
            JsonNode page = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId,
                    "/api/projects/" + projectId + "/suggestions?status=PENDING&size=50", null).getBody());
            for (JsonNode node : page.path("content")) {
                if (suggestionId.equals(node.path("id").asString())) last = node;
            }
            if (last != null && state.equals(last.path("code").path("verifyState").asString())) return last;
            Thread.sleep(200);
        }
        throw new AssertionError("Suggestion never reached verification state " + state + ": " + last);
    }

    /**
     * The models for these tests. Generation: a requirement typed in the chat comes back as one story (about
     * cryptocurrency payments when the text says so); when the prompt carries the client's code it is flagged
     * against the first module, as the real model is asked to. A chat message that mentions "código" is a
     * question the index cannot answer, so the stub asks for the deep search. Verification: reads the passages
     * it is given, counts its calls, and fails on demand.
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
                public AssistantReply converse(String message, List<ChatTurn> history, GenerationContext context,
                                               BacklogOverview overview) {
                    if (message.contains("código")) {
                        return new AssistantReply("Busco en el código.", null, "es", true);
                    }
                    return new AssistantReply("", message, null);
                }

                @Override
                public GenerationResult generate(String transcript, String language,
                                                 @Nullable GenerationContext context) {
                    boolean crypto = transcript.contains("blockchain");
                    boolean flagged = transcript.contains("[[fail]]");
                    StoryInsight insight = StoryInsight.evidence(transcript);
                    if (!crypto && context != null && context.code() != null && !context.code().modules().isEmpty()) {
                        GenerationContext.CodeModuleEntry module = context.code().modules().getFirst();
                        insight = new StoryInsight(transcript, CodeFinding.CONFLICTS_WITH_CODE,
                                "El código permite cancelar hasta 2 h antes; el cliente pide 24 h",
                                List.of(new CodeReference(module.repository(), module.path(), module.name(),
                                        module.url())));
                    }
                    return new GenerationResult(List.of(crypto
                            ? new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                                    "Pagar con blockchain", "usuario", "pagar con una wallet blockchain",
                                    "no usar tarjeta", Priority.LOW, 5, List.of(), null, null, insight)
                            : new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                                    flagged ? "Cancelar reserva con aviso previo" : "Cancelar reserva hasta 24 horas antes",
                                    "comensal",
                                    flagged ? "cancelar mi reserva con aviso previo [[fail]]" : "cancelar mi reserva hasta 24 horas antes",
                                    "no pagar por una mesa que no usaré",
                                    Priority.HIGH, 3, List.of(), null, null, insight)));
                }
            };
        }

        @Bean
        @Primary
        RecordingVerifier recordingVerifier() {
            return new RecordingVerifier();
        }

        @Bean
        VerificationEvents verificationEvents() {
            return new VerificationEvents();
        }
    }

    /** Reads the passages like the model would: a cancellation limit in the code is a conflict with a 24 h ask. */
    static class RecordingVerifier implements CodeVerificationPort {

        final AtomicInteger calls = new AtomicInteger();
        volatile List<CodeSnippet> lastSnippets = List.of();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public CodeVerification verify(String subject, SubjectKind kind, List<CodeSnippet> snippets, String language) {
            calls.incrementAndGet();
            lastSnippets = snippets;
            if (subject.contains("[[fail]]")) {
                throw new IllegalStateException("quota exceeded");
            }
            for (CodeSnippet snippet : snippets) {
                String[] lines = snippet.text().split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (!lines[i].contains("CANCELLATION_LIMIT_HOURS = 2")) continue;
                    int line = snippet.lineStart() + i;
                    String folder = snippet.path().substring(0, snippet.path().lastIndexOf('/'));
                    CodeReference reference = new CodeReference(snippet.repository(), folder,
                            snippet.path().substring(snippet.path().lastIndexOf('/') + 1),
                            snippet.url().replaceAll("#.*$", "") + "#L" + line, snippet.path(), line, line,
                            lines[i].strip());
                    return kind == SubjectKind.QUESTION
                            ? new CodeVerification(CodeVerdict.ALREADY_EXISTS,
                                    "Sí: el código permite cancelar hasta 2 horas antes.", List.of(reference))
                            : new CodeVerification(CodeVerdict.CONFLICTS_WITH_CODE,
                                    "El código permite cancelar hasta 2 h antes; el cliente pide 24 h",
                                    List.of(reference));
                }
            }
            return new CodeVerification(CodeVerdict.UNSURE, "No alcanza para decidirlo.", List.of());
        }
    }

    /** Collects the verification events the realtime cards are built from. */
    static class VerificationEvents {

        final List<SuggestionCodeVerifiedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(SuggestionCodeVerifiedEvent event) {
            events.add(event);
        }

        List<CodeVerifyState> statesOf(String suggestionId) {
            return events.stream().filter(e -> e.suggestionId().toString().equals(suggestionId))
                    .map(SuggestionCodeVerifiedEvent::state).toList();
        }
    }

    // ----- helpers -----

    private static Map<String, Object> install(long installationId, String state, String code) {
        return Map.of("installationId", installationId, "setupAction", "install", "state", state, "code", code);
    }

    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private ResponseEntity<String> webhook(String event, String payload, String signature) {
        return client().post().uri("/api/code/webhooks/github")
                .header("X-GitHub-Event", event)
                .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                .header("X-Hub-Signature-256", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload.getBytes(StandardCharsets.UTF_8))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private JsonNode awaitCommit(UUID orgId, String repos, String repoId, String sha) throws InterruptedException {
        JsonNode last = null;
        for (int i = 0; i < 150; i++) {
            last = find(JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, repos, null).getBody()), "id", repoId);
            if ("READY".equals(last.path("status").asString()) && sha.equals(last.path("commitSha").asString())) {
                return last;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Repository never indexed " + sha + ": " + last);
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.path(field).asString()));
        return out;
    }

    private JsonNode awaitStatus(UUID orgId, String repos, String repoId, String status) throws InterruptedException {
        return awaitStatus(orgId, repos, repoId, status, false);
    }

    private JsonNode awaitStatus(UUID orgId, String repos, String repoId, String status, boolean failureExpected)
            throws InterruptedException {
        JsonNode last = null;
        for (int i = 0; i < 150; i++) {
            JsonNode list = JSON.readTree(send(HttpMethod.GET, OWNER_USER_ID, orgId, repos, null).getBody());
            last = find(list, "id", repoId);
            if (status.equals(last.path("status").asString())) return last;
            if (!failureExpected && "FAILED".equals(last.path("status").asString())) {
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
