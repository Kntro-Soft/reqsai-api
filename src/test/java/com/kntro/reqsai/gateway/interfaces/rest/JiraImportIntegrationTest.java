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
 * End-to-end test of the ASYNC Jira IMPORT slice: connects Jira at the org level, sets a project target,
 * then starts the import job (202 + RUNNING snapshot), polls the job endpoint until the Spring Batch
 * execution COMPLETEs in the right tenant schema, and asserts stories were created with a near-duplicate
 * counted as processed-but-skipped (both stubbed issues map to the same stubbed generation output). Also
 * asserts the batch metadata landed in the global {@code public.batch_*} tables, not a tenant schema.
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

        // Start the import job: 202 Accepted with a RUNNING snapshot.
        ResponseEntity<String> importRes = client().post()
                .uri("/api/projects/{p}/integration/jira/import", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(importRes.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode accepted = JSON.readTree(importRes.getBody());
        assertThat(accepted.get("jobType").asText()).isEqualTo("IMPORT");
        assertThat(accepted.get("status").asText()).isEqualTo("RUNNING");
        String jobId = accepted.get("id").asText();

        // The RUNNING job is visible to the reload-recovery query while it lasts, and the terminal
        // state is reached by polling the job endpoint (the batch runs on the async executor).
        JsonNode result = awaitJobCompletion(projectId, jobId, orgId);

        // Both stubbed issues map (via the stubbed generation) to the same story, so the second is a
        // duplicate: processed but neither succeeded nor failed, summarized in the message.
        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.get("total").asInt()).isEqualTo(2);
        assertThat(result.get("processed").asInt()).isEqualTo(2);
        assertThat(result.get("succeeded").asInt()).isEqualTo(1);
        assertThat(result.get("failed").asInt()).isZero();
        assertThat(result.get("message").asText()).isEqualTo("1 duplicados omitidos");
        assertThat(result.hasNonNull("finishedAt")).isTrue();

        // Exactly one story persisted in the tenant backlog — written from the batch thread, proving
        // the tenant context captured at launch was restored by the job listener.
        Integer storyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".user_stories WHERE project_id = ?::uuid",
                Integer.class, projectId.toString());
        assertThat(storyCount).isEqualTo(1);

        // Spring Batch metadata lands in the global public schema (schema-qualified table prefix).
        Integer batchInstances = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.batch_job_instance WHERE job_name = 'jiraImportJob'",
                Integer.class);
        assertThat(batchInstances).isGreaterThanOrEqualTo(1);

        // The jobs listing returns the finished job (most recent first).
        ResponseEntity<String> listRes = client().get()
                .uri("/api/projects/{p}/integration/jira/jobs", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode jobsList = JSON.readTree(listRes.getBody());
        assertThat(jobsList.isArray()).isTrue();
        assertThat(jobsList.get(0).get("id").asText()).isEqualTo(jobId);
    }

    /** Polls {@code GET .../jobs/{jobId}} until the job leaves RUNNING (max ~60s). */
    private JsonNode awaitJobCompletion(UUID projectId, String jobId, String orgId) throws Exception {
        JsonNode job = null;
        for (int i = 0; i < 200; i++) {
            ResponseEntity<String> res = client().get()
                    .uri("/api/projects/{p}/integration/jira/jobs/{j}", projectId, jobId)
                    .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                    .header("Api-Version", "1")
                    .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                            .body(response.bodyTo(String.class)));
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            job = JSON.readTree(res.getBody());
            if (!"RUNNING".equals(job.get("status").asText())) {
                return job;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Job " + jobId + " did not finish in time: " + job);
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
