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
@DisplayName("Integration: Project Access (implicit owner/admin + project RBAC)")
class ProjectAccessIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000003";
    private static final String MANAGER_USER_ID = "00000000-0000-0000-0000-000000000005";
    private static final String TARGET_USER_ID = "00000000-0000-0000-0000-000000000006";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("owner and admin access all projects; member sees only assigned; member denied on unassigned")
    void implicit_access_for_owner_admin_and_scoped_member() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "tenant_" + slug;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID, "email", "admin@example.com", "displayName", "Admin", "role", "ADMIN"));
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"));
        UUID memberId = memberId(orgId, "member@example.com");

        UUID assigned = createProjectAndReturnId(orgId, slug, "Assigned Project");
        UUID unassigned = createProjectAndReturnId(orgId, slug, "Unassigned Project");

        String roleId = createRoleAndReturnId(orgId, assigned, OWNER_USER_ID, "Reader", List.of("MEMBER_READ"), schema);
        assignMember(orgId, assigned, OWNER_USER_ID, memberId.toString(), roleId);

        // Owner sees both projects
        ResponseEntity<String> ownerList = listProjects(orgId, OWNER_USER_ID);
        assertThat(ownerList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownerList.getBody()).contains("Assigned Project", "Unassigned Project");

        // Admin sees both projects (implicit access, no explicit assignment)
        ResponseEntity<String> adminList = listProjects(orgId, ADMIN_USER_ID);
        assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminList.getBody()).contains("Assigned Project", "Unassigned Project");

        // Member sees only the assigned project
        ResponseEntity<String> memberList = listProjects(orgId, MEMBER_USER_ID);
        assertThat(memberList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberList.getBody()).contains("Assigned Project");
        assertThat(memberList.getBody()).doesNotContain("Unassigned Project");

        // Owner/admin can GET any project
        assertThat(getProject(orgId, unassigned, OWNER_USER_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getProject(orgId, unassigned, ADMIN_USER_ID).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Member can GET assigned project but is denied on the unassigned one
        assertThat(getProject(orgId, assigned, MEMBER_USER_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getProject(orgId, unassigned, MEMBER_USER_ID).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("project member with MEMBER_INVITE permission can manage members; without it (ROLE_CREATE) is denied")
    void project_permission_enforced_on_member_management() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "tenant_" + slug;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        // A manager (has MANAGE_MEMBERS via project role), a plain member (no manage permission), and a target.
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MANAGER_USER_ID, "email", "manager@example.com", "displayName", "Manager", "role", "MEMBER"));
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"));
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", TARGET_USER_ID, "email", "target@example.com", "displayName", "Target", "role", "MEMBER"));

        UUID managerId = memberId(orgId, "manager@example.com");
        UUID plainMemberId = memberId(orgId, "member@example.com");
        UUID targetId = memberId(orgId, "target@example.com");

        UUID projectId = createProjectAndReturnId(orgId, slug, "RBAC Project");

        String managerRoleId = createRoleAndReturnId(orgId, projectId, OWNER_USER_ID, "Lead",
                List.of("MEMBER_READ", "MEMBER_INVITE"), schema);
        String readerRoleId = createRoleAndReturnId(orgId, projectId, OWNER_USER_ID, "Reader",
                List.of("MEMBER_READ"), schema);

        // Assign the manager (with MANAGE_MEMBERS) and the plain member (read only) to the project.
        assignMember(orgId, projectId, OWNER_USER_ID, managerId.toString(), managerRoleId);
        assignMember(orgId, projectId, OWNER_USER_ID, plainMemberId.toString(), readerRoleId);

        // Manager (project MANAGE_MEMBERS) can assign the target member.
        ResponseEntity<String> byManager = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/members", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(MANAGER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("memberId", targetId.toString(), "roleId", readerRoleId))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(byManager.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Plain member (only READ_PROJECT) cannot create a project role.
        ResponseEntity<String> roleByPlainMember = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/roles", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(MEMBER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Sneaky", "permissions", List.of("MEMBER_READ")))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(roleByPlainMember.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> listProjects(UUID orgId, String userId) {
        return client().get().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> getProject(UUID orgId, UUID projectId, String userId) {
        return client().get().uri("/api/organizations/{orgId}/projects/{projectId}", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private void assignMember(UUID orgId, UUID projectId, String callerUserId, String memberId, String roleId) {
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/members", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(callerUserId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("memberId", memberId, "roleId", roleId))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private UUID memberId(UUID orgId, String email) {
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, email));
    }

    private UUID createOrganizationAndReturnId(String suffix, String expectedSlug) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug));
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

        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), projectName));
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

    private void createMember(UUID orgId, String callerUserId, Map<String, Object> body) {
        ResponseEntity<String> response = client().post().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(callerUserId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
