package com.kntro.reqsai.gateway.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.gateway.StubJiraOAuthConfig;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubEmbeddingConfig;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the Jira OAuth 2.0 (3LO) slice: creates an org, fetches the authorize URL (obtaining
 * a real signed state), completes the callback (single accessible site auto-selects), then sets a target,
 * seeds a story and pushes it. Asserts an OAUTH2 connection persists with ENCRYPTED tokens (never echoed)
 * and that the push routes to the {@code api.atlassian.com/ex/jira/{cloudId}} OAuth base.
 * <p>
 * The Atlassian/Jira HTTP boundary is stubbed via {@link StubJiraOAuthConfig} — no real network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({StubJiraOAuthConfig.class, StubEmbeddingConfig.class})
@Tag("integration")
@DisplayName("Integration: Jira OAuth connect, target and push")
class JiraOAuthIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubJiraOAuthConfig.RecordingJiraClient recordingJiraClient;

    @Test
    @DisplayName("completes the OAuth callback, persists encrypted tokens and pushes via the OAuth base")
    void oauth_connect_target_and_push() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "oauth-" + suffix;
        String schema = "tenant_" + slug;
        String orgId = createOrg(suffix, slug);
        UUID projectId = createProject(orgId, schema, "OAuth Platform " + suffix);

        // 1. Get the authorize URL -> yields a real signed state bound to this org+user.
        ResponseEntity<String> authUrlRes = client().get()
                .uri("/api/organizations/{orgId}/integrations/jira/oauth/authorize-url", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(authUrlRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode authUrl = JSON.readTree(authUrlRes.getBody());
        assertThat(authUrl.get("url").asText()).startsWith("https://auth.atlassian.com/authorize");
        String state = authUrl.get("state").asText();

        // 2. Complete the callback (single site auto-selects) -> 201 OAUTH2 connection, tokens NOT echoed.
        ResponseEntity<String> callbackRes = client().post()
                .uri("/api/organizations/{orgId}/integrations/jira/oauth/callback", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "auth-code", "state", state))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));

        assertThat(callbackRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode conn = JSON.readTree(callbackRes.getBody());
        assertThat(conn.get("provider").asText()).isEqualTo("JIRA");
        assertThat(conn.get("credentialType").asText()).isEqualTo("OAUTH2");
        assertThat(conn.get("siteUrl").asText()).isEqualTo("https://acme.atlassian.net");
        assertThat(conn.hasNonNull("email")).isFalse(); // email is null for OAUTH2
        assertThat(callbackRes.getBody()).doesNotContain("access-token-1");
        assertThat(callbackRes.getBody()).doesNotContain("refresh-token-1");
        String connectionId = conn.get("id").asText();

        // The stored OAuth tokens are ciphertext (BYTEA), not the plaintext values; cloud_id + type persist.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT credential_type, cloud_id, email, secret_ciphertext, "
                        + "encode(oauth_refresh_ciphertext, 'escape') AS refresh_txt, "
                        + "encode(oauth_access_ciphertext, 'escape') AS access_txt "
                        + "FROM \"" + schema + "\".integration_connections WHERE id = ?::uuid", connectionId);
        assertThat(row.get("credential_type")).isEqualTo("OAUTH2");
        assertThat(row.get("cloud_id")).isEqualTo("cloud-1");
        assertThat(row.get("email")).isNull();
        assertThat(row.get("secret_ciphertext")).isNull();
        assertThat((String) row.get("refresh_txt")).doesNotContain("refresh-token-1");
        assertThat((String) row.get("access_txt")).doesNotContain("access-token-1");

        // 3. Seed a story, set the target, push -> routes to the OAuth API base.
        String storyId = seedStory(projectId, orgId);
        setTarget(projectId, orgId, connectionId);
        recordingJiraClient.apiBases.clear();

        ResponseEntity<String> pushRes = client().post()
                .uri("/api/projects/{p}/integration/jira/stories/{s}/push", projectId, storyId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));

        assertThat(pushRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode push = JSON.readTree(pushRes.getBody());
        assertThat(push.get("jiraIssueKey").asText()).isEqualTo("PAY-42");
        // The push routed through the OAuth base, not the API-token site base.
        assertThat(recordingJiraClient.apiBases)
                .allSatisfy(base -> assertThat(base).isEqualTo("https://api.atlassian.com/ex/jira/cloud-1/rest/api/3"));
    }

    private String seedStory(UUID projectId, String orgId) throws Exception {
        ResponseEntity<String> storyRes = client().post().uri("/api/projects/{p}/stories", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "OAuth push", "role", "analyst",
                        "action", "push via oauth", "benefit", "no api token", "priority", "HIGH"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(storyRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JSON.readTree(storyRes.getBody()).get("id").asText();
    }

    private void setTarget(UUID projectId, String orgId, String connectionId) {
        ResponseEntity<String> targetRes = client().put()
                .uri("/api/projects/{p}/integration/jira/target", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("connectionId", connectionId, "jiraProjectKey", "PAY", "issueTypeName", "Story"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(targetRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID createProject(String orgId, String schema, String name) {
        client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "programmingLanguages", List.of("Java"),
                        "frameworks", List.of("Spring Boot"), "clientPlatforms", List.of("Web"),
                        "databases", List.of("PostgreSQL"), "architecture", "Hexagonal", "domain", "Fintech"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".projects WHERE name = ?", String.class, name));
    }

    private String createOrg(String suffix, String expectedSlug) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Oauth " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
    }
}
