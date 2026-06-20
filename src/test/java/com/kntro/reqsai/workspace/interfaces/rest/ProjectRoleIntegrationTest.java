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
@DisplayName("Integration: Project Roles")
class ProjectRoleIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000003";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create list get update and delete project roles")
    void should_create_list_get_update_and_delete_project_roles() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);
        UUID projectId = createProjectAndReturnId(orgId, slug, "Roles Project");
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID,
                "email", "admin@example.com",
                "displayName", "Admin User",
                "role", "ADMIN"));

        ResponseEntity<String> created = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/roles", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Analyst",
                        "permissions", List.of("READ_PROJECT", "RUN_DISCOVERY")))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"name\":\"Analyst\"");

        String schema = "tenant_" + slug;
        String roleId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".project_roles WHERE project_id = ?::uuid AND name = ?",
                String.class, projectId, "Analyst");

        ResponseEntity<String> list = client().get().uri("/api/organizations/{orgId}/projects/{projectId}/roles", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("\"name\":\"Analyst\"");

        ResponseEntity<String> getOne = client().get().uri("/api/organizations/{orgId}/projects/{projectId}/roles/{roleId}",
                        orgId, projectId, UUID.fromString(roleId))
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(getOne.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> update = client().put().uri("/api/organizations/{orgId}/projects/{projectId}/roles/{roleId}",
                        orgId, projectId, UUID.fromString(roleId))
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Lead Analyst",
                        "permissions", List.of("READ_PROJECT", "MANAGE_MEMBERS")))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getBody()).contains("\"name\":\"Lead Analyst\"");

        ResponseEntity<Void> delete = client().delete().uri("/api/organizations/{orgId}/projects/{projectId}/roles/{roleId}",
                        orgId, projectId, UUID.fromString(roleId))
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).build());
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".project_roles WHERE id = ?::uuid",
                Integer.class, UUID.fromString(roleId));
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("should reject project role creation for regular member")
    void should_reject_project_role_creation_for_regular_member() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);
        UUID projectId = createProjectAndReturnId(orgId, slug, "Forbidden Roles Project");
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID,
                "email", "member@example.com",
                "displayName", "Regular Member",
                "role", "MEMBER"));

        ResponseEntity<String> forbidden = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/roles", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(MEMBER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Analyst", "permissions", List.of("READ_PROJECT")))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID createOrganizationAndReturnId(String suffix, String expectedSlug) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?",
                String.class, expectedSlug);
        return UUID.fromString(orgIdStr);
    }

    private UUID createProjectAndReturnId(UUID orgId, String slug, String projectName) {
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
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
                        "domain", "Fintech"))
                .exchange((req, resSpec) -> ResponseEntity.status(resSpec.getStatusCode()).body(resSpec.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String projectId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), projectName);
        return UUID.fromString(projectId);
    }

    private void createMember(UUID orgId, String userId, Map<String, Object> body) {
        ResponseEntity<String> response = client().post().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
