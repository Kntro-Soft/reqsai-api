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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Change Member Role")
class ChangeMemberRoleIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000003";
    private static final String OTHER_ADMIN_USER_ID = "00000000-0000-0000-0000-000000000004";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port:0}")
    private int serverPort;

    /**
     * A RestClient backed by the JDK HttpClient, which (unlike {@code SimpleClientHttpRequestFactory})
     * supports the HTTP PATCH method used by the change-role endpoint.
     */
    private RestClient patchClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    @Test
    @DisplayName("owner promotes a member to admin and then demotes back")
    void owner_promotes_and_demotes() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Regular Member", "role", "MEMBER"), HttpStatus.CREATED);
        UUID memberId = memberId(orgId, "member@example.com");

        ResponseEntity<String> promote = changeRole(orgId, memberId, OWNER_USER_ID, "ADMIN");
        assertThat(promote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promote.getBody()).contains("\"role\":\"ADMIN\"");
        assertThat(roleOf(memberId)).isEqualTo("ADMIN");

        ResponseEntity<String> demote = changeRole(orgId, memberId, OWNER_USER_ID, "MEMBER");
        assertThat(demote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleOf(memberId)).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("admin changes a member role")
    void admin_changes_member() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID, "email", "admin@example.com",
                "displayName", "Admin User", "role", "ADMIN"), HttpStatus.CREATED);
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Regular Member", "role", "MEMBER"), HttpStatus.CREATED);
        UUID memberId = memberId(orgId, "member@example.com");

        ResponseEntity<String> promote = changeRole(orgId, memberId, ADMIN_USER_ID, "ADMIN");
        assertThat(promote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleOf(memberId)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("admin cannot change own, another admin's, or the owner member role; member is blocked; OWNER is rejected")
    void rbac_denials() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", ADMIN_USER_ID, "email", "admin@example.com",
                "displayName", "Admin User", "role", "ADMIN"), HttpStatus.CREATED);
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", OTHER_ADMIN_USER_ID, "email", "admin2@example.com",
                "displayName", "Other Admin", "role", "ADMIN"), HttpStatus.CREATED);
        createMember(orgId, OWNER_USER_ID, Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Regular Member", "role", "MEMBER"), HttpStatus.CREATED);

        UUID adminId = memberId(orgId, "admin@example.com");
        UUID otherAdminId = memberId(orgId, "admin2@example.com");
        UUID memberId = memberId(orgId, "member@example.com");

        // Admin changing their own role -> forbidden
        assertThat(changeRole(orgId, adminId, ADMIN_USER_ID, "MEMBER").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Admin changing another admin -> forbidden
        assertThat(changeRole(orgId, otherAdminId, ADMIN_USER_ID, "MEMBER").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Member changing anyone -> forbidden
        assertThat(changeRole(orgId, adminId, MEMBER_USER_ID, "MEMBER").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Setting OWNER role -> forbidden even for owner
        assertThat(changeRole(orgId, memberId, OWNER_USER_ID, "OWNER").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Roles unchanged
        assertThat(roleOf(adminId)).isEqualTo("ADMIN");
        assertThat(roleOf(otherAdminId)).isEqualTo("ADMIN");
        assertThat(roleOf(memberId)).isEqualTo("MEMBER");
    }

    private ResponseEntity<String> changeRole(UUID orgId, UUID memberId, String callerUserId, String role) {
        return patchClient().patch().uri("/api/organizations/{orgId}/members/{memberId}", orgId, memberId)
                .header("Authorization", TestJwtFactory.bearer(callerUserId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("role", role))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private String roleOf(UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT role FROM public.members WHERE id = ?::uuid", String.class, memberId);
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

    private void createMember(UUID orgId, String callerUserId, Map<String, Object> body, HttpStatus expected) {
        ResponseEntity<String> response = client().post().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(callerUserId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }
}
