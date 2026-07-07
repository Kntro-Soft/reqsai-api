package com.kntro.reqsai.gateway.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.gateway.StubJiraProviderConfig;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubEmbeddingConfig;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the Jira IMPORT slice: connects Jira at the org level, sets a project target, then
 * imports the two stubbed Jira issues into the backlog as user stories — asserting stories are created and
 * that a near-duplicate is skipped (both stubbed issues map to the same stubbed generation output, so the
 * second is detected as a duplicate of the first via the deterministic embedding stub).
 *
 * <p>The Jira boundary is stubbed via {@link StubJiraProviderConfig} (no real network) and the LLM via
 * {@link StubRequirementGenerationConfig} (no real model — this test is NOT tagged {@code llm}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({StubJiraProviderConfig.class, StubEmbeddingConfig.class, StubRequirementGenerationConfig.class})
@Tag("integration")
@DisplayName("Integration: Jira import (preview + import)")
class JiraImportIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("previews candidates then imports: creates stories and skips a duplicate")
    void previews_then_imports() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "tenant_" + slug;
        String orgId = createOrg(suffix, slug);
        UUID projectId = createProject(orgId, schema, "Payment Platform");

        String connectionId = connectAndReadId(orgId, schema);
        setTarget(orgId, projectId, connectionId);

        // Preview lists both stubbed issues; neither is a duplicate yet (empty backlog).
        ResponseEntity<String> previewRes = client().get()
                .uri("/api/projects/{p}/integration/jira/import/preview", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(previewRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode preview = JSON.readTree(previewRes.getBody());
        assertThat(preview.get("total").asInt()).isEqualTo(2);
        assertThat(preview.get("issues")).hasSize(2);

        // Import all eligible issues.
        ResponseEntity<String> importRes = client().post()
                .uri("/api/projects/{p}/integration/jira/import", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(importRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode result = JSON.readTree(importRes.getBody());

        // Both stubbed issues map (via the stubbed generation) to the same story, so the second collides.
        assertThat(result.get("imported").asInt()).isEqualTo(1);
        assertThat(result.get("skipped").asInt()).isEqualTo(1);
        assertThat(result.get("failed").asInt()).isZero();
        assertThat(result.get("results")).hasSize(2);
        boolean hasImported = false;
        boolean hasDuplicate = false;
        for (JsonNode r : result.get("results")) {
            if ("imported".equals(r.get("status").asText())) {
                hasImported = true;
                assertThat(r.hasNonNull("storyId")).isTrue();
            } else if ("duplicate".equals(r.get("status").asText())) {
                hasDuplicate = true;
                assertThat(r.hasNonNull("storyId")).isFalse();
            }
        }
        assertThat(hasImported).isTrue();
        assertThat(hasDuplicate).isTrue();

        // Exactly one story persisted in the tenant backlog.
        Integer storyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".user_stories WHERE project_id = ?::uuid",
                Integer.class, projectId.toString());
        assertThat(storyCount).isEqualTo(1);
    }

    @Test
    @DisplayName("returns 409 when importing with no target configured")
    void import_without_target() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix, "acme-" + suffix);
        UUID projectId = UUID.randomUUID();

        ResponseEntity<String> res = client().post()
                .uri("/api/projects/{p}/integration/jira/import", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("INTEGRATION_TARGET_NOT_CONFIGURED");
    }

    private String connectAndReadId(String orgId, String schema) throws Exception {
        ResponseEntity<String> connectRes = client().post()
                .uri("/api/organizations/{orgId}/integrations/jira", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("siteUrl", "https://acme.atlassian.net", "email", "pm@acme.com", "apiToken", "tok"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(connectRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JSON.readTree(connectRes.getBody()).get("id").asText();
    }

    private void setTarget(String orgId, UUID projectId, String connectionId) {
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
