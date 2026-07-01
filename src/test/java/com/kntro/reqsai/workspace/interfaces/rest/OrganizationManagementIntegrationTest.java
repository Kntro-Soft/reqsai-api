package com.kntro.reqsai.workspace.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Organization management")
class OrganizationManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000003";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port:0}")
    private int serverPort;

    @Test
    @DisplayName("batch invite creates pending members atomically")
    void batch_invite_creates_pending_members() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix, slug);

        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/members/batch", orgId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("invitations", List.of(
                        Map.of("email", "one@example.com", "displayName", "One", "role", "MEMBER"),
                        Map.of("email", "two@example.com", "displayName", "Two", "role", "ADMIN"))))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).contains("one@example.com", "two@example.com", "\"status\":\"PENDING\"");
    }

    @Test
    @DisplayName("batch invite with a duplicate email fails and persists nothing")
    void batch_invite_duplicate_fails_atomically() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix, slug);

        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/members/batch", orgId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("invitations", List.of(
                        Map.of("email", "dup@example.com", "displayName", "One", "role", "MEMBER"),
                        Map.of("email", "dup@example.com", "displayName", "Two", "role", "MEMBER"))))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                Integer.class, orgId, "dup@example.com");
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("owner transfers ownership to an active member")
    void owner_transfers_ownership() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID, "email", "admin@example.com", "displayName", "Admin", "role", "ADMIN"));
        String adminMemberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, "admin@example.com");

        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/transfer-ownership", orgId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newOwnerMemberId", adminMemberId))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"ownerId\":\"" + ADMIN_USER_ID + "\"");

        String newOwner = jdbcTemplate.queryForObject(
                "SELECT owner_id::text FROM public.organizations WHERE id = ?::uuid", String.class, orgId);
        assertThat(newOwner).isEqualTo(ADMIN_USER_ID);
    }

    @Test
    @DisplayName("active member can leave but the owner cannot")
    void member_leaves_owner_cannot() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"));

        ResponseEntity<Void> leave = client().delete().uri("/api/organizations/{orgId}/members/me", orgId)
                .header("Authorization", TestJwtFactory.bearer(MEMBER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).build());
        assertThat(leave.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> ownerLeave = client().delete().uri("/api/organizations/{orgId}/members/me", orgId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).build());
        assertThat(ownerLeave.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("owner deactivates and reactivates a member via status endpoint")
    void deactivate_and_reactivate_member() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"));
        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, "member@example.com");

        ResponseEntity<String> deactivate = changeStatus(orgId, memberId, "INACTIVE");
        assertThat(deactivate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM public.members WHERE id = ?::uuid", String.class, memberId))
                .isEqualTo("INACTIVE");

        ResponseEntity<String> reactivate = changeStatus(orgId, memberId, "ACTIVE");
        assertThat(reactivate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM public.members WHERE id = ?::uuid", String.class, memberId))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("owner deletes org: soft-deletes registry and drops tenant schema")
    void owner_deletes_organization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix, slug);

        ResponseEntity<Void> del = client().delete().uri("/api/organizations/{orgId}", orgId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).build());
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM public.organizations WHERE id = ?::uuid", String.class, orgId))
                .isEqualTo("DELETED");
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, "tenant_" + slug);
        assertThat(schemaCount).isZero();
    }

    @Test
    @DisplayName("non-owner cannot delete the organization")
    void non_owner_cannot_delete() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID, "email", "admin@example.com", "displayName", "Admin", "role", "ADMIN"));

        ResponseEntity<Void> del = client().delete().uri("/api/organizations/{orgId}", orgId)
                .header("Authorization", TestJwtFactory.bearer(ADMIN_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).build());
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** JDK HttpClient supports PATCH (SimpleClientHttpRequestFactory does not). */
    private RestClient patchClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    private ResponseEntity<String> changeStatus(UUID orgId, String memberId, String status) {
        return patchClient().patch().uri("/api/organizations/{orgId}/members/{memberId}/status", orgId, memberId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
    }

    private UUID createOrg(String suffix, String expectedSlug) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        return UUID.fromString(orgIdStr);
    }

    private void createMember(UUID orgId, String userId, Map<String, Object> body) {
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
