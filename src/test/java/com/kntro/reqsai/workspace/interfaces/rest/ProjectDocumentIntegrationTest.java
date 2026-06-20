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
@DisplayName("Integration: Create Project Document")
class ProjectDocumentIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create project document in tenant schema")
    void should_create_project_document_in_tenant_schema() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "Documents Project");

        ResponseEntity<String> response = addProjectDocument(orgId, projectId, Map.of(
                "name", "Business Rules v1",
                "documentType", "BUSINESS_RULES"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"name\":\"Business Rules v1\"");
        assertThat(response.getBody()).contains("\"documentType\":\"BUSINESS_RULES\"");
        assertThat(response.getBody()).contains("\"status\":\"ACTIVE\"");

        String schema = "tenant_" + expectedSlug;
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id::text as id, name, document_type, status FROM \"" + schema + "\".project_documents WHERE project_id = ?::uuid",
                projectId);
        assertThat(row.get("name")).isEqualTo("Business Rules v1");
        assertThat(row.get("document_type")).isEqualTo("BUSINESS_RULES");
        assertThat(row.get("status")).isEqualTo("ACTIVE");

        String documentId = (String) row.get("id");

        ResponseEntity<String> getOne = client().get().uri("/api/organizations/{orgId}/projects/{projectId}/documents/{documentId}",
                        orgId, projectId, UUID.fromString(documentId))
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, responseSpec) -> ResponseEntity.status(responseSpec.getStatusCode())
                        .body(responseSpec.bodyTo(String.class)));
        assertThat(getOne.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getOne.getBody()).contains("\"id\":\"" + documentId + "\"");
        assertThat(getOne.getBody()).contains("\"name\":\"Business Rules v1\"");

        ResponseEntity<String> list = client().get().uri("/api/organizations/{orgId}/projects/{projectId}/documents", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, responseSpec) -> ResponseEntity.status(responseSpec.getStatusCode())
                        .body(responseSpec.bodyTo(String.class)));
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("\"name\":\"Business Rules v1\"");

        ResponseEntity<String> update = client().put().uri("/api/organizations/{orgId}/projects/{projectId}/documents/{documentId}",
                        orgId, projectId, UUID.fromString(documentId))
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Technical Spec v1", "documentType", "TECHNICAL_SPEC"))
                .exchange((req, responseSpec) -> ResponseEntity.status(responseSpec.getStatusCode())
                        .body(responseSpec.bodyTo(String.class)));
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getBody()).contains("\"name\":\"Technical Spec v1\"");
        assertThat(update.getBody()).contains("\"documentType\":\"TECHNICAL_SPEC\"");

        ResponseEntity<Void> delete = client().delete().uri("/api/organizations/{orgId}/projects/{projectId}/documents/{documentId}",
                        orgId, projectId, UUID.fromString(documentId))
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, responseSpec) -> ResponseEntity.status(responseSpec.getStatusCode()).build());
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer countAfterDelete = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".project_documents WHERE id = ?::uuid",
                Integer.class, UUID.fromString(documentId));
        assertThat(countAfterDelete).isEqualTo(0);
    }

    @Test
    @DisplayName("should reject duplicate document name ignoring case")
    void should_reject_duplicate_document_name_ignoring_case() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "Duplicate Documents Project");

        ResponseEntity<String> first = addProjectDocument(orgId, projectId, Map.of(
                "name", "Business Rules v1",
                "documentType", "BUSINESS_RULES"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> duplicate = addProjectDocument(orgId, projectId, Map.of(
                "name", "  business rules v1  ",
                "documentType", "REFERENCE"));

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("should reject blank name")
    void should_reject_blank_name() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "Validation Documents Project");

        ResponseEntity<String> response = addProjectDocument(orgId, projectId, Map.of(
                "name", "   ",
                "documentType", "BUSINESS_RULES"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should reject creating document in archived project")
    void should_reject_creating_document_in_archived_project() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "Archived Documents Project");

        ResponseEntity<Void> archiveResponse = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/archive", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode()).build());
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> response = addProjectDocument(orgId, projectId, Map.of(
                "name", "Business Rules v1",
                "documentType", "BUSINESS_RULES"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should reject unauthenticated request")
    void should_reject_unauthenticated_request() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, expectedSlug);
        UUID projectId = createProjectAndReturnId(orgId, expectedSlug, "No Auth Documents Project");

        ResponseEntity<String> response = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/documents", orgId, projectId)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Business Rules v1", "documentType", "BUSINESS_RULES"))
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

    private ResponseEntity<String> addProjectDocument(UUID orgId, UUID projectId, Map<String, Object> body) {
        return client().post().uri("/api/organizations/{orgId}/projects/{projectId}/documents", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }
}
