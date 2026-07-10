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
@DisplayName("Integration: Project Members")
class ProjectMemberIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000003";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create list get update and delete project assignments")
    void should_create_list_get_update_and_delete_project_assignments() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);
        UUID projectId = createProjectAndReturnId(orgId, slug, "Assignments Project");

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID,
                "email", "admin@example.com",
                "displayName", "Admin User",
                "role", "ADMIN"));
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID,
                "email", "member@example.com",
                "displayName", "Regular Member",
                "role", "MEMBER"));

        String schema = "tenant_" + slug;
        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, "member@example.com");

        String roleAId = createRoleAndReturnId(orgId, projectId, ADMIN_USER_ID, "Analyst",
                List.of("MEMBER_READ", "DOCUMENT_READ"), schema);
        String roleBId = createRoleAndReturnId(orgId, projectId, ADMIN_USER_ID, "Lead Analyst",
                List.of("MEMBER_READ", "MEMBER_INVITE"), schema);

        ResponseEntity<String> created = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/members", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("memberId", memberId, "roleId", roleAId))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String assignmentId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".project_members WHERE project_id = ?::uuid AND member_id = ?::uuid",
                String.class, projectId, UUID.fromString(memberId));

        ResponseEntity<String> list = client().get().uri("/api/organizations/{orgId}/projects/{projectId}/members", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains(assignmentId);
        // The list embeds the role's display name so viewers see roles without ROLE_READ.
        assertThat(list.getBody()).contains("\"roleName\":\"Analyst\"");

        ResponseEntity<String> getOne = client().get().uri("/api/organizations/{orgId}/projects/{projectId}/members/{assignmentId}",
                        orgId, projectId, UUID.fromString(assignmentId))
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(getOne.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getOne.getBody()).contains("\"memberId\":\"" + memberId + "\"");

        ResponseEntity<String> update = client().put().uri("/api/organizations/{orgId}/projects/{projectId}/members/{assignmentId}",
                        orgId, projectId, UUID.fromString(assignmentId))
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("roleId", roleBId))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getBody()).contains("\"roleId\":\"" + roleBId + "\"");

        ResponseEntity<Void> delete = client().delete().uri("/api/organizations/{orgId}/projects/{projectId}/members/{assignmentId}",
                        orgId, projectId, UUID.fromString(assignmentId))
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).build());
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".project_members WHERE id = ?::uuid",
                Integer.class, UUID.fromString(assignmentId));
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("should reject assignment for non admin member")
    void should_reject_assignment_for_non_admin_member() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);
        UUID projectId = createProjectAndReturnId(orgId, slug, "Forbidden Assignments Project");

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID,
                "email", "member@example.com",
                "displayName", "Regular Member",
                "role", "MEMBER"));
        String schema = "tenant_" + slug;
        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, "member@example.com");
        String roleId = createRoleAndReturnId(orgId, projectId, OWNER_USER_ID, "Analyst", List.of("MEMBER_READ"), schema);

        ResponseEntity<String> forbidden = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/members", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(MEMBER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("memberId", memberId, "roleId", roleId))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("member with only MEMBER_READ can list members and sees role names without ROLE_READ")
    void member_with_member_read_can_list_members() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);
        // Pin the base floor to NONE so access rests purely on the assigned role — the exact
        // scenario a member whose only project role grants MEMBER_READ lands in.
        jdbcTemplate.update("UPDATE public.organizations SET member_base_permission = 'NONE' WHERE id = ?", orgId);
        UUID projectId = createProjectAndReturnId(orgId, slug, "Viewer Project");

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID,
                "email", "member@example.com",
                "displayName", "Regular Member",
                "role", "MEMBER"));
        String schema = "tenant_" + slug;
        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, "member@example.com");
        String roleId = createRoleAndReturnId(orgId, projectId, OWNER_USER_ID, "Viewer", List.of("MEMBER_READ"), schema);

        ResponseEntity<String> assigned = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/members", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("memberId", memberId, "roleId", roleId))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // The member themselves — holding only MEMBER_READ via the assigned role — lists members.
        ResponseEntity<String> list = client().get().uri("/api/organizations/{orgId}/projects/{projectId}/members", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(MEMBER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("\"roleName\":\"Viewer\"");
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

    private String createRoleAndReturnId(
            UUID orgId, UUID projectId, String userId, String name, List<String> permissions, String schema) {
        ResponseEntity<String> response = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/roles", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "permissions", permissions))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".project_roles WHERE project_id = ?::uuid AND name = ?",
                String.class, projectId, name);
    }
}
