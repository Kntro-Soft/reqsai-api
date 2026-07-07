package com.kntro.reqsai.discovery.interfaces.rest;

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

/**
 * Verifies the {@code @PreAuthorize} gates on the discovery REST endpoints: org owners bypass,
 * members need a project role carrying the {@code SESSION_*}/{@code STORY_*} permission, and
 * session-scoped routes resolve the project through the session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Discovery access control (project permissions)")
class DiscoveryAccessControlIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String READER_USER_ID = "00000000-0000-0000-0000-000000000007";
    private static final String OUTSIDER_USER_ID = "00000000-0000-0000-0000-000000000008";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("owner passes; reader (SESSION_READ/STORY_READ) can read but not run/write; unassigned member denied")
    void discovery_endpoints_enforce_project_permissions() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String schema = "tenant_" + slug;
        UUID orgId = createOrganizationAndReturnId(suffix, slug);

        createMember(orgId, Map.of(
                "userId", READER_USER_ID, "email", "reader@example.com", "displayName", "Reader", "role", "MEMBER"));
        createMember(orgId, Map.of(
                "userId", OUTSIDER_USER_ID, "email", "outsider@example.com", "displayName", "Outsider", "role", "MEMBER"));
        UUID readerId = memberId(orgId, "reader@example.com");

        UUID projectId = createProjectAndReturnId(orgId, slug);
        String readerRoleId = createRoleAndReturnId(orgId, projectId, "Session Reader",
                List.of("SESSION_READ", "STORY_READ"), schema);
        assignMember(orgId, projectId, readerId.toString(), readerRoleId);

        // Owner creates a session (SESSION_RUN via owner bypass)
        ResponseEntity<String> created = post(OWNER_USER_ID, orgId,
                "/api/projects/" + projectId + "/sessions",
                Map.of("title", "Access Control Meeting", "language", "es-PE"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID sessionId = UUID.fromString(created.getBody().split("\"id\":\"")[1].split("\"")[0]);

        // Reader can list and get sessions (SESSION_READ)
        assertThat(get(READER_USER_ID, orgId, "/api/projects/" + projectId + "/sessions").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get(READER_USER_ID, orgId, "/api/projects/" + projectId + "/sessions/" + sessionId).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Reader cannot create or start sessions (needs SESSION_RUN)
        assertThat(post(READER_USER_ID, orgId, "/api/projects/" + projectId + "/sessions",
                Map.of("title", "Sneaky", "language", "es-PE")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(READER_USER_ID, orgId,
                "/api/projects/" + projectId + "/sessions/" + sessionId + "/start", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Session-scoped route: reader can read the transcript, but cannot process it
        assertThat(get(READER_USER_ID, orgId, "/api/sessions/" + sessionId + "/transcript").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post(READER_USER_ID, orgId, "/api/sessions/" + sessionId + "/process", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Session-scoped route: reader lacks SESSION_DECIDE
        assertThat(post(READER_USER_ID, orgId,
                "/api/sessions/" + sessionId + "/suggestions/" + UUID.randomUUID() + "/dismiss", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Reader can read stories but cannot create them (needs STORY_WRITE)
        assertThat(get(READER_USER_ID, orgId, "/api/projects/" + projectId + "/stories").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post(READER_USER_ID, orgId, "/api/projects/" + projectId + "/stories",
                Map.of("title", "Story", "role", "user", "action", "log in", "benefit", "access", "priority", "HIGH"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Unassigned member is denied even on reads
        assertThat(get(OUTSIDER_USER_ID, orgId, "/api/projects/" + projectId + "/sessions").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(OUTSIDER_USER_ID, orgId, "/api/sessions/" + sessionId + "/transcript").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Missing session passes the gate and surfaces as 404 (not 403) for an authorized caller
        assertThat(get(OWNER_USER_ID, orgId, "/api/sessions/" + UUID.randomUUID() + "/transcript").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----- helpers -----

    private ResponseEntity<String> get(String userId, UUID orgId, String uri) {
        return client().get().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> post(String userId, UUID orgId, String uri, Map<String, Object> body) {
        var spec = client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1");
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
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

    private void createMember(UUID orgId, Map<String, Object> body) {
        ResponseEntity<String> response = post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private UUID memberId(UUID orgId, String email) {
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, email));
    }

    private UUID createProjectAndReturnId(UUID orgId, String slug) {
        ResponseEntity<String> res = post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects", Map.of(
                "name", "Discovery Project",
                "description", "Project description",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Clean Architecture",
                "domain", "Fintech"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Discovery Project"));
    }

    private String createRoleAndReturnId(UUID orgId, UUID projectId, String name, List<String> permissions, String schema) {
        ResponseEntity<String> response = post(OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/projects/" + projectId + "/roles",
                Map.of("name", name, "permissions", permissions));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".project_roles WHERE project_id = ?::uuid AND name = ?",
                String.class, projectId, name);
    }

    private void assignMember(UUID orgId, UUID projectId, String memberId, String roleId) {
        ResponseEntity<String> res = post(OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/projects/" + projectId + "/members",
                Map.of("memberId", memberId, "roleId", roleId));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
