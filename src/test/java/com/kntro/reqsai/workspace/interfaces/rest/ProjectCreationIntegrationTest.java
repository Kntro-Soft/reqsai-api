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
@DisplayName("Integration: Create Project")
class ProjectCreationIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create a project and provision its glossary in the tenant schema")
    void should_create_project_and_provision_glossary() {
        // Arrange - create organization to provision schema
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        UUID orgId = UUID.fromString(orgIdStr);

        Map<String, Object> request = Map.of(
                "name", "My First Project",
                "description", "A cool project",
                "programmingLanguages", List.of("Java", "TypeScript"),
                "frameworks", List.of("Spring Boot", "Next.js"),
                "clientPlatforms", List.of("Web", "Mobile"),
                "databases", List.of("PostgreSQL", "Redis"),
                "architecture", "Clean Architecture",
                "domain", "Fintech"
        );

        // Act - create project
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert - HTTP
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).contains("\"name\":\"My First Project\"");
        assertThat(res.getBody()).contains("\"status\":\"ACTIVE\"");

        // Find created project ID from response or DB
        String schema = "tenant_" + expectedSlug;
        Map<String, Object> projectRow = jdbcTemplate.queryForMap(
                "SELECT id::text as id, name FROM \"" + schema + "\".projects WHERE organization_id = ?::uuid",
                orgId
        );
        assertThat(projectRow.get("name")).isEqualTo("My First Project");
        UUID projectId = UUID.fromString((String) projectRow.get("id"));

        // Assert - glossary auto-created (1:1 with project) in the tenant schema
        Integer glossaryCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".glossaries WHERE project_id = ?::uuid",
                Integer.class, projectId
        );
        assertThat(glossaryCount).isEqualTo(1);

        // Update Project
        Map<String, Object> updateRequest = Map.of(
                "name", "My Updated Project",
                "description", "An updated project description",
                "programmingLanguages", List.of("Kotlin"),
                "frameworks", List.of("Micronaut"),
                "clientPlatforms", List.of("Mobile"),
                "databases", List.of("MongoDB"),
                "architecture", "Microservices",
                "domain", "Logistics"
        );

        ResponseEntity<String> updateRes = client().put().uri("/api/organizations/{orgId}/projects/{projectId}", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateRequest)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateRes.getBody()).contains("\"name\":\"My Updated Project\"");
        assertThat(updateRes.getBody()).contains("\"description\":\"An updated project description\"");

        // Assert DB updated
        Map<String, Object> updatedProjectRow = jdbcTemplate.queryForMap(
                "SELECT name, description FROM \"" + schema + "\".projects WHERE id = ?::uuid",
                projectId
        );
        assertThat(updatedProjectRow.get("name")).isEqualTo("My Updated Project");
        assertThat(updatedProjectRow.get("description")).isEqualTo("An updated project description");

        // Archive Project
        ResponseEntity<Void> archiveRes = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/archive", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).build());

        assertThat(archiveRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Assert project archived and glossary preserved
        String statusAfterArchive = jdbcTemplate.queryForObject(
                "SELECT status FROM \"" + schema + "\".projects WHERE id = ?::uuid",
                String.class, projectId
        );
        assertThat(statusAfterArchive).isEqualTo("ARCHIVED");

        Integer glossaryCountAfterArchive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".glossaries WHERE project_id = ?::uuid",
                Integer.class, projectId
        );
        assertThat(glossaryCountAfterArchive).isEqualTo(1);

        // Delete Project physically
        ResponseEntity<Void> deleteRes = client().delete().uri("/api/organizations/{orgId}/projects/{projectId}", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).build());

        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer projectCountAfterDelete = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".projects WHERE id = ?::uuid",
                Integer.class, projectId
        );
        assertThat(projectCountAfterDelete).isEqualTo(0);

        Integer glossaryCountAfterDelete = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".glossaries WHERE project_id = ?::uuid",
                Integer.class, projectId
        );
        assertThat(glossaryCountAfterDelete).isEqualTo(0);
    }

    @Test
    @DisplayName("should reject unauthenticated request")
    void should_reject_unauthenticated() {
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", UUID.randomUUID())
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Project No Auth"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should get a project by id within its organization")
    void should_get_project_by_id_within_organization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        UUID orgId = UUID.fromString(orgIdStr);

        Map<String, Object> request = Map.of(
                "name", "Lookup Project",
                "description", "Lookup",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Clean Architecture",
                "domain", "Fintech"
        );

        ResponseEntity<String> createRes = client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgIdStr, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String schema = "tenant_" + expectedSlug;
        String projectId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Lookup Project");

        ResponseEntity<String> getRes = client().get().uri("/api/organizations/{orgId}/projects/{projectId}", orgId, UUID.fromString(projectId))
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgIdStr, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getBody()).contains("\"id\":\"" + projectId + "\"");
        assertThat(getRes.getBody()).contains("\"name\":\"Lookup Project\"");
    }

    @Test
    @DisplayName("should list only projects from the requested organization")
    void should_list_only_projects_from_the_requested_organization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        UUID orgA = UUID.fromString(orgIdStr);
        UUID orgB = UUID.randomUUID();
        String schema = "tenant_" + expectedSlug;
        String otherSlug = "delta-" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update(
                "INSERT INTO public.organizations (id, name, slug, owner_id, status, meeting_language, audio_retention_days, " +
                        "max_members, max_projects, max_documents_per_project, max_tokens_per_month, max_glossary_terms_per_project, " +
                        "created_at, updated_at, created_by, updated_by) " +
                        "VALUES (?::uuid, ?, ?, ?::uuid, 'ACTIVE', 'es-PE', 30, 3, 1, 10, 100000, 50, now(), now(), ?::uuid, ?::uuid)",
                orgB.toString(), "Delta " + otherSlug, otherSlug, USER_ID, USER_ID, USER_ID);

        jdbcTemplate.update(
                "INSERT INTO \"" + schema + "\".projects (id, organization_id, name, description, programming_languages, frameworks, client_platforms, databases, architecture, domain, status, created_at, updated_at) " +
                        "VALUES (?::uuid, ?::uuid, ?, ?, ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[], ?, ?, 'ACTIVE', now(), now())",
                UUID.randomUUID().toString(), orgA.toString(), "Org A Project", "A",
                "{\"Java\"}", "{\"Spring Boot\"}", "{\"Web\"}", "{\"PostgreSQL\"}", "Clean Architecture", "Fintech");
        jdbcTemplate.update(
                "INSERT INTO \"" + schema + "\".projects (id, organization_id, name, description, programming_languages, frameworks, client_platforms, databases, architecture, domain, status, created_at, updated_at) " +
                        "VALUES (?::uuid, ?::uuid, ?, ?, ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[], ?, ?, 'ACTIVE', now(), now())",
                UUID.randomUUID().toString(), orgB.toString(), "Org B Project", "B",
                "{\"Java\"}", "{\"Spring Boot\"}", "{\"Web\"}", "{\"PostgreSQL\"}", "Clean Architecture", "Fintech");

        ResponseEntity<String> listRes = client().get().uri("/api/organizations/{orgId}/projects?size=20&sortBy=name&sortDirection=ASC", orgA)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgIdStr, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listRes.getBody()).contains("\"name\":\"Org A Project\"");
        assertThat(listRes.getBody()).doesNotContain("\"name\":\"Org B Project\"");
        assertThat(listRes.getBody()).contains("\"totalElements\":1");
    }

    @Test
    @DisplayName("should allow the same project name in different organizations of the same tenant schema")
    void should_allow_same_project_name_in_different_organizations() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        UUID orgA = UUID.fromString(orgIdStr);
        UUID orgB = UUID.randomUUID();
        String schema = "tenant_" + expectedSlug;
        String otherSlug = "beta-" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update(
                "INSERT INTO public.organizations (id, name, slug, owner_id, status, meeting_language, audio_retention_days, " +
                        "max_members, max_projects, max_documents_per_project, max_tokens_per_month, max_glossary_terms_per_project, " +
                        "created_at, updated_at, created_by, updated_by) " +
                        "VALUES (?::uuid, ?, ?, ?::uuid, 'ACTIVE', 'es-PE', 30, 3, 1, 10, 100000, 50, now(), now(), ?::uuid, ?::uuid)",
                orgB.toString(), "Beta " + otherSlug, otherSlug, USER_ID, USER_ID, USER_ID);

        jdbcTemplate.update(
                "INSERT INTO \"" + schema + "\".projects (id, organization_id, name, description, programming_languages, frameworks, client_platforms, databases, architecture, domain, status, created_at, updated_at) " +
                        "VALUES (?::uuid, ?::uuid, ?, ?, ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[], ?, ?, 'ACTIVE', now(), now())",
                UUID.randomUUID().toString(), orgA.toString(), "Shared Name", "Existing",
                "{\"Java\"}", "{\"Spring Boot\"}", "{\"Web\"}", "{\"PostgreSQL\"}", "Clean Architecture", "Fintech");

        Map<String, Object> request = Map.of(
                "name", "Shared Name",
                "description", "Another org same name",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Clean Architecture",
                "domain", "Fintech"
        );

        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", orgB)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgIdStr, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".projects WHERE name = ?",
                Integer.class, "Shared Name");
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("should enforce project limit only within the target organization")
    void should_enforce_project_limit_only_within_target_organization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        UUID orgA = UUID.fromString(orgIdStr);
        UUID orgB = UUID.randomUUID();
        String schema = "tenant_" + expectedSlug;
        String otherSlug = "gamma-" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update(
                "INSERT INTO public.organizations (id, name, slug, owner_id, status, meeting_language, audio_retention_days, " +
                        "max_members, max_projects, max_documents_per_project, max_tokens_per_month, max_glossary_terms_per_project, " +
                        "created_at, updated_at, created_by, updated_by) " +
                        "VALUES (?::uuid, ?, ?, ?::uuid, 'ACTIVE', 'es-PE', 30, 3, 1, 10, 100000, 50, now(), now(), ?::uuid, ?::uuid)",
                orgB.toString(), "Gamma " + otherSlug, otherSlug, USER_ID, USER_ID, USER_ID);

        jdbcTemplate.update(
                "INSERT INTO \"" + schema + "\".projects (id, organization_id, name, description, programming_languages, frameworks, client_platforms, databases, architecture, domain, status, created_at, updated_at) " +
                        "VALUES (?::uuid, ?::uuid, ?, ?, ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[], ?, ?, 'ACTIVE', now(), now())",
                UUID.randomUUID().toString(), orgA.toString(), "Org A Existing", "Existing",
                "{\"Java\"}", "{\"Spring Boot\"}", "{\"Web\"}", "{\"PostgreSQL\"}", "Clean Architecture", "Fintech");

        Map<String, Object> request = Map.of(
                "name", "Org B First",
                "description", "Should pass",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Clean Architecture",
                "domain", "Fintech"
        );

        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", orgB)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgIdStr, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).contains("\"name\":\"Org B First\"");
    }
}
