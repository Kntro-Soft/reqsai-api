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
@DisplayName("Integration: Member base permission floor + effective permission endpoints")
class BasePermissionIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000003";
    private static final String ROLE_MEMBER_USER_ID = "00000000-0000-0000-0000-000000000004";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PUT base-permission: owner can set it, a plain member is forbidden")
    void put_base_permission_owner_ok_member_forbidden() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"));

        // Owner sets the floor to NONE
        ResponseEntity<String> byOwner = putBasePermission(orgId, OWNER_USER_ID, "NONE");
        assertThat(byOwner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byOwner.getBody()).contains("\"basePermission\":\"NONE\"");

        // GET reflects it
        ResponseEntity<String> get = getBasePermission(orgId, OWNER_USER_ID);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody()).contains("\"basePermission\":\"NONE\"");

        // A plain member cannot change or read it
        assertThat(putBasePermission(orgId, MEMBER_USER_ID, "READ").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getBasePermission(orgId, MEMBER_USER_ID).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("role-less member reads a CONSTRAINT_READ endpoint when base=READ, is denied when base=NONE")
    void base_floor_gates_a_read_endpoint_for_a_role_less_member() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        // A member with NO project assignment at all.
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"));

        UUID projectId = createProjectAndReturnId(orgId, slug, "Floor Project");

        // base=READ (the default) → the role-less member can read constraints.
        assertThat(putBasePermission(orgId, OWNER_USER_ID, "READ").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listConstraints(orgId, projectId, MEMBER_USER_ID).getStatusCode()).isEqualTo(HttpStatus.OK);

        // base=NONE → the same member is now forbidden.
        assertThat(putBasePermission(orgId, OWNER_USER_ID, "NONE").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listConstraints(orgId, projectId, MEMBER_USER_ID).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("me/permissions: owner gets the full catalog; role-less member gets the floor; role member gets floor + role")
    void me_permissions_reflects_owner_floor_and_role() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "tenant_" + slug;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"));
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ROLE_MEMBER_USER_ID, "email", "writer@example.com", "displayName", "Writer", "role", "MEMBER"));
        UUID writerMemberId = memberId(orgId, "writer@example.com");

        UUID projectId = createProjectAndReturnId(orgId, slug, "Perms Project");

        // base=READ so the role-less member has the read floor.
        assertThat(putBasePermission(orgId, OWNER_USER_ID, "READ").getStatusCode()).isEqualTo(HttpStatus.OK);

        // A custom role carrying a WRITE the floor does not grant, assigned to the writer member.
        String roleId = createRoleAndReturnId(orgId, projectId, OWNER_USER_ID, "Writer",
                List.of("CONSTRAINT_READ", "CONSTRAINT_WRITE"), schema);
        assignMember(orgId, projectId, OWNER_USER_ID, writerMemberId.toString(), roleId);

        // Owner → full catalog (includes writes and manage permissions).
        ResponseEntity<String> owner = myPermissions(projectId, OWNER_USER_ID, orgId);
        assertThat(owner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.getBody()).contains("STORY_READ", "STORY_WRITE", "PROJECT_DELETE", "ROLE_CREATE");

        // Role-less member → only the READ floor (7 *_READ perms, no writes).
        ResponseEntity<String> member = myPermissions(projectId, MEMBER_USER_ID, orgId);
        assertThat(member.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(member.getBody()).contains("STORY_READ", "DOCUMENT_READ", "CONSTRAINT_READ");
        assertThat(member.getBody()).doesNotContain("STORY_WRITE", "CONSTRAINT_WRITE", "PROJECT_DELETE");

        // Writer member → the READ floor plus the role's CONSTRAINT_WRITE, but no unrelated writes.
        ResponseEntity<String> writer = myPermissions(projectId, ROLE_MEMBER_USER_ID, orgId);
        assertThat(writer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(writer.getBody()).contains("CONSTRAINT_WRITE", "STORY_READ");
        assertThat(writer.getBody()).doesNotContain("STORY_WRITE", "PROJECT_DELETE");
    }

    @Test
    @DisplayName("me/authorization returns the caller's org role and the base-permission floor")
    void me_authorization_returns_role_and_floor() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID, "email", "admin@example.com", "displayName", "Admin", "role", "ADMIN"));
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"));

        assertThat(putBasePermission(orgId, OWNER_USER_ID, "NONE").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> owner = myAuthorization(orgId, OWNER_USER_ID);
        assertThat(owner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.getBody()).contains("\"orgRole\":\"OWNER\"", "\"memberBasePermission\":\"NONE\"");

        ResponseEntity<String> admin = myAuthorization(orgId, ADMIN_USER_ID);
        assertThat(admin.getBody()).contains("\"orgRole\":\"ADMIN\"");

        ResponseEntity<String> member = myAuthorization(orgId, MEMBER_USER_ID);
        assertThat(member.getBody()).contains("\"orgRole\":\"MEMBER\"", "\"memberBasePermission\":\"NONE\"");
    }

    // --- request helpers --------------------------------------------------------------------------

    private ResponseEntity<String> putBasePermission(UUID orgId, String userId, String value) {
        return client().put().uri("/api/organizations/{orgId}/base-permission", orgId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("basePermission", value))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> getBasePermission(UUID orgId, String userId) {
        return client().get().uri("/api/organizations/{orgId}/base-permission", orgId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> myAuthorization(UUID orgId, String userId) {
        return client().get().uri("/api/organizations/{orgId}/me/authorization", orgId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> myPermissions(UUID projectId, String userId, UUID orgId) {
        return client().get().uri("/api/projects/{projectId}/me/permissions", projectId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> listConstraints(UUID orgId, UUID projectId, String userId) {
        return client().get().uri("/api/organizations/{orgId}/projects/{projectId}/constraints", orgId, projectId)
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
