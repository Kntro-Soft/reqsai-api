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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Members")
class MemberIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000003";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create list get and deactivate members in shared schema")
    void should_create_list_get_and_deactivate_members_in_shared_schema() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        ResponseEntity<String> createdAdmin = createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID,
                "email", "admin@example.com",
                "displayName", "Admin User",
                "role", "ADMIN"));
        assertThat(createdAdmin.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String adminMemberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, "admin@example.com");

        ResponseEntity<String> createdPending = createMember(orgId, ADMIN_USER_ID, Map.of(
                "email", "invitee@example.com",
                "displayName", "Pending Invitee",
                "role", "MEMBER"));
        assertThat(createdPending.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdPending.getBody()).contains("\"status\":\"PENDING\"");

        ResponseEntity<String> list = client().get().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("admin@example.com", "invitee@example.com");

        ResponseEntity<String> getOne = client().get().uri("/api/organizations/{orgId}/members/{memberId}",
                        orgId, UUID.fromString(adminMemberId))
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(getOne.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getOne.getBody()).contains("\"email\":\"admin@example.com\"");

        ResponseEntity<Void> delete = client().delete().uri("/api/organizations/{orgId}/members/{memberId}",
                        orgId, UUID.fromString(adminMemberId))
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).build());
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM public.members WHERE id = ?::uuid",
                String.class, UUID.fromString(adminMemberId));
        assertThat(status).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("should reject member creation for non admin member")
    void should_reject_member_creation_for_non_admin_member() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        ResponseEntity<String> createdMember = createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID,
                "email", "member@example.com",
                "displayName", "Regular Member",
                "role", "MEMBER"));
        assertThat(createdMember.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> forbidden = createMember(orgId, MEMBER_USER_ID, Map.of(
                "email", "another@example.com",
                "displayName", "Another Member",
                "role", "MEMBER"));
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should let a regular member view the roster")
    void should_let_a_regular_member_view_the_roster() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        ResponseEntity<String> createdMember = createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID,
                "email", "member@example.com",
                "displayName", "Regular Member",
                "role", "MEMBER"));
        assertThat(createdMember.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> list = client().get().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(MEMBER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("member@example.com");
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

    private ResponseEntity<String> createMember(UUID orgId, String userId, Map<String, Object> body) {
        return client().post().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }
}
