package com.kntro.reqsai.workspace.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Add Glossary Term")
class GlossaryTermIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create glossary term in tenant schema")
    void should_create_glossary_term_in_tenant_schema() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "Glossary Project");

        ResponseEntity<String> response = addGlossaryTerm(orgId, projectId, expectedSlug,
                Map.of("term", "Lead", "definition", "Potential customer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"term\":\"Lead\"");
        assertThat(response.getBody()).contains("\"definition\":\"Potential customer\"");

        String schema = "tenant_" + expectedSlug;
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT term, definition FROM \"" + schema + "\".glossary_terms WHERE glossary_id = (" +
                        "SELECT id FROM \"" + schema + "\".glossaries WHERE project_id = ?::uuid)",
                projectId);
        assertThat(row.get("term")).isEqualTo("Lead");
        assertThat(row.get("definition")).isEqualTo("Potential customer");
    }

    @Test
    @DisplayName("should reject duplicate term ignoring case")
    void should_reject_duplicate_term_ignoring_case() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "Duplicate Project");

        ResponseEntity<String> first = addGlossaryTerm(orgId, projectId, expectedSlug,
                Map.of("term", "Lead", "definition", "Potential customer"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> duplicate = addGlossaryTerm(orgId, projectId, expectedSlug,
                Map.of("term", " lead ", "definition", "Another definition"));

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("should reject blank definition")
    void should_reject_blank_definition() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "Validation Project");

        ResponseEntity<String> response = addGlossaryTerm(orgId, projectId, expectedSlug,
                Map.of("term", "Lead", "definition", "   "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should reject adding term to archived project")
    void should_reject_adding_term_to_archived_project() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "Archived Project");

        ResponseEntity<Void> archiveResponse = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/archive", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode()).build());
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> response = addGlossaryTerm(orgId, projectId, expectedSlug,
                Map.of("term", "Lead", "definition", "Potential customer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should reject unauthenticated request")
    void should_reject_unauthenticated_request() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "No Auth Project");

        ResponseEntity<String> response = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/glossary", orgId, projectId)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("term", "Lead", "definition", "Potential customer"))
                .exchange((request, responseSpec) -> ResponseEntity.status(responseSpec.getStatusCode())
                        .body(responseSpec.bodyTo(String.class)), false);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private UUID createOrganizationAndReturnId(String suffix, String expectedSlug) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        return UUID.fromString(orgIdStr);
    }

    private UUID createProjectAndReturnId(UUID orgId, String expectedSlug, String projectName) {
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", projectName,
                        "description", "Project description",
                        "programmingLanguages", List.of("Java"),
                        "frameworks", List.of("Spring Boot"),
                        "clientPlatforms", List.of("Web"),
                        "databases", List.of("PostgreSQL"),
                        "architecture", "Clean Architecture",
                        "domain", "Fintech"
                ))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String schema = "tenant_" + expectedSlug;
        String projectId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), projectName);
        return UUID.fromString(projectId);
    }

    private ResponseEntity<String> addGlossaryTerm(UUID orgId, UUID projectId, String expectedSlug, Map<String, Object> body) {
        return client().post().uri("/api/organizations/{orgId}/projects/{projectId}/glossary", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }
}
