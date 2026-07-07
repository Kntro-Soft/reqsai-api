package com.kntro.reqsai.gateway.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.gateway.StubJiraProviderConfig;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the Jira integration slice across the full multitenant flow: creates an org
 * (provisioning its tenant schema with the V21 integration tables), connects Jira at the org level
 * (persisting an ENCRYPTED token), sets a project target, seeds a story, and pushes it — asserting the
 * connection/target rows persist (token encrypted, never echoed) and the push maps to a Jira issue.
 * <p>
 * The Jira RestClient boundary is stubbed via {@link StubJiraProviderConfig} so nothing hits real Jira.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({StubJiraProviderConfig.class, StubEmbeddingConfig.class})
@Tag("integration")
@DisplayName("Integration: Jira connection, target and push")
class JiraIntegrationPushIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("connects Jira, sets a target and pushes a story end-to-end")
    void connects_targets_and_pushes() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "tenant_" + slug;
        String orgId = createOrg(suffix, slug);
        UUID projectId = createProject(orgId, schema, "Payment Platform");

        // Connect Jira at the org level (verify is stubbed to succeed) -> 201, token NOT echoed
        ResponseEntity<String> connectRes = client().post()
                .uri("/api/organizations/{orgId}/integrations/jira", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("siteUrl", "https://acme.atlassian.net",
                        "email", "pm@acme.com", "apiToken", "super-secret-token"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));

        assertThat(connectRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(connectRes.getBody()).contains("\"provider\":\"JIRA\"");
        assertThat(connectRes.getBody()).doesNotContain("super-secret-token");
        String connectionId = JSON.readTree(connectRes.getBody()).get("id").asText();

        // The stored secret is ciphertext (BYTEA), not the plaintext token.
        String storedHex = jdbcTemplate.queryForObject(
                "SELECT encode(secret_ciphertext, 'escape') FROM \"" + schema + "\".integration_connections WHERE id = ?::uuid",
                String.class, connectionId);
        assertThat(storedHex).doesNotContain("super-secret-token");

        // Seed a story in the tenant.
        ResponseEntity<String> storyRes = client().post().uri("/api/projects/{p}/stories", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Bulk import", "role", "analyst",
                        "action", "upload a CSV", "benefit", "save time", "priority", "HIGH"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(storyRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String storyId = JSON.readTree(storyRes.getBody()).get("id").asText();

        // Set the project target.
        ResponseEntity<String> targetRes = client().put()
                .uri("/api/projects/{p}/integration/jira/target", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("connectionId", connectionId, "jiraProjectKey", "PAY", "issueTypeName", "Story"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(targetRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(targetRes.getBody()).contains("\"jiraProjectKey\":\"PAY\"");

        Integer targetCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".project_integration_targets WHERE project_id = ?::uuid",
                Integer.class, projectId.toString());
        assertThat(targetCount).isEqualTo(1);

        // Push the story -> mapped to a Jira issue key/url by the stub provider.
        ResponseEntity<String> pushRes = client().post()
                .uri("/api/projects/{p}/integration/jira/stories/{s}/push", projectId, storyId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));

        assertThat(pushRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode push = JSON.readTree(pushRes.getBody());
        assertThat(push.get("storyId").asText()).isEqualTo(storyId);
        assertThat(push.get("jiraIssueKey").asText()).startsWith("PAY-");
        assertThat(push.get("jiraIssueUrl").asText()).startsWith("https://acme.atlassian.net/browse/PAY-");
        assertThat(push.hasNonNull("error")).isFalse();

        // push-all also succeeds for the single seeded story.
        ResponseEntity<String> pushAllRes = client().post()
                .uri("/api/projects/{p}/integration/jira/stories/push-all", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(pushAllRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode all = JSON.readTree(pushAllRes.getBody());
        assertThat(all.get("pushed").asInt()).isEqualTo(1);
        assertThat(all.get("failed").asInt()).isZero();
    }

    @Test
    @DisplayName("rejects a second active Jira connection with 409")
    void rejects_duplicate_connection() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix, "acme-" + suffix);

        connectJira(orgId);
        ResponseEntity<String> second = connectJira(orgId);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("INTEGRATION_ALREADY_CONNECTED");
    }

    @Test
    @DisplayName("returns 409 when pushing with no target configured")
    void push_without_target() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix, "acme-" + suffix);
        UUID projectId = UUID.randomUUID();

        ResponseEntity<String> res = client().post()
                .uri("/api/projects/{p}/integration/jira/stories/{s}/push", projectId, UUID.randomUUID())
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("INTEGRATION_TARGET_NOT_CONFIGURED");
    }

    private ResponseEntity<String> connectJira(String orgId) {
        return client().post().uri("/api/organizations/{orgId}/integrations/jira", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("siteUrl", "https://acme.atlassian.net", "email", "pm@acme.com", "apiToken", "tok"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private UUID createProject(String orgId, String schema, String name) {
        client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "programmingLanguages", java.util.List.of("Java"),
                        "frameworks", java.util.List.of("Spring Boot"), "clientPlatforms", java.util.List.of("Web"),
                        "databases", java.util.List.of("PostgreSQL"), "architecture", "Hexagonal", "domain", "Fintech"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".projects WHERE name = ?", String.class, name));
    }

    private String createOrg(String suffix, String expectedSlug) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
    }
}
