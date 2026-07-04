package com.kntro.reqsai.workspace.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.CapturingEmailConfig;
import com.kntro.reqsai.testsupport.CapturingEmailConfig.InvitationTokenCapture;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@Import(CapturingEmailConfig.class)
@DisplayName("Integration: Invitations")
class InvitationIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000010";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private InvitationTokenCapture tokenCapture;

    @Test
    @DisplayName("invite creates a pending invitation, accept links and activates the member")
    void invite_then_accept() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID orgId = createOrg(suffix);
        String inviteeEmail = "invitee-" + suffix + "@example.com";

        ResponseEntity<String> invited = invite(orgId, inviteeEmail, "Invitee");
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, inviteeEmail);
        // one PENDING invitation exists for the member
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.invitations WHERE member_id = ?::uuid AND status = 'PENDING'",
                Integer.class, memberId);
        assertThat(pending).isEqualTo(1);

        String rawToken = awaitToken(inviteeEmail);

        // public GET returns the accept/signup shape
        ResponseEntity<String> details = getByToken(rawToken);
        assertThat(details.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(details.getBody()).contains("\"email\":\"" + inviteeEmail + "\"", "\"role\":\"MEMBER\"",
                "\"status\":\"PENDING\"", "\"expired\":false");

        // the invited person has an account/user with the matching email
        UUID inviteeUserId = seedUser(inviteeEmail);

        ResponseEntity<String> accepted = accept(rawToken, inviteeUserId, orgId);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody()).contains("\"organizationId\":\"" + orgId + "\"", "\"role\":\"MEMBER\"");

        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM public.invitations WHERE member_id = ?::uuid", String.class, memberId))
                .isEqualTo("ACCEPTED");

        // idempotent replay
        ResponseEntity<String> replay = accept(rawToken, inviteeUserId, orgId);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("invite-to-project then accept materializes a ProjectMember assignment")
    void invite_to_project_then_accept_materializes_assignment() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix);
        String schema = "tenant_" + slug;
        UUID projectId = createProject(orgId, slug, "Invite Project " + suffix);
        String roleId = createRole(orgId, projectId, "Analyst", List.of("MEMBER_READ", "DOCUMENT_READ"), schema);

        String inviteeEmail = "invitee-" + suffix + "@example.com";
        ResponseEntity<String> invited = client().post()
                .uri("/api/organizations/{orgId}/projects/{projectId}/members/invite", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("invitations", List.of(
                        Map.of("email", inviteeEmail, "displayName", "Invitee", "roleId", roleId))))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, inviteeEmail);
        // the pending invitation records the project target
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_project_id::text FROM public.invitations WHERE member_id = ?::uuid AND status = 'PENDING'",
                String.class, memberId)).isEqualTo(projectId.toString());

        String rawToken = awaitToken(inviteeEmail);
        UUID inviteeUserId = seedUser(inviteeEmail);

        ResponseEntity<String> accepted = accept(rawToken, inviteeUserId, orgId);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");

        // a ProjectMember assignment now exists for that member + project + role
        Integer assignments = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".project_members "
                        + "WHERE project_id = ?::uuid AND member_id = ?::uuid AND role_id = ?::uuid",
                Integer.class, projectId, UUID.fromString(memberId), UUID.fromString(roleId));
        assertThat(assignments).isEqualTo(1);
    }

    @Test
    @DisplayName("invite-to-project with a role from another project is rejected")
    void invite_to_project_rejects_foreign_role() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrg(suffix);
        String schema = "tenant_" + slug;
        UUID projectId = createProject(orgId, slug, "Target Project " + suffix);
        UUID otherProjectId = createProject(orgId, slug, "Other Project " + suffix);
        String foreignRoleId = createRole(orgId, otherProjectId, "Analyst", List.of("MEMBER_READ"), schema);

        ResponseEntity<String> res = client().post()
                .uri("/api/organizations/{orgId}/projects/{projectId}/members/invite", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("invitations", List.of(
                        Map.of("email", "x-" + suffix + "@example.com", "displayName", "X", "roleId", foreignRoleId))))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("accept by a caller whose email differs from the invited email is forbidden")
    void accept_email_mismatch_forbidden() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID orgId = createOrg(suffix);
        String inviteeEmail = "invitee-" + suffix + "@example.com";
        assertThat(invite(orgId, inviteeEmail, "Invitee").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String rawToken = awaitToken(inviteeEmail);

        UUID otherUserId = seedUser("someone-else-" + suffix + "@example.com");
        ResponseEntity<String> forbidden = accept(rawToken, otherUserId, orgId);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("unknown token returns 404 on the public lookup")
    void unknown_token_not_found() {
        ResponseEntity<String> res = getByToken("does-not-exist");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("resend supersedes the previous invitation and issues a new token")
    void resend_supersedes_previous() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID orgId = createOrg(suffix);
        String inviteeEmail = "invitee-" + suffix + "@example.com";
        assertThat(invite(orgId, inviteeEmail, "Invitee").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstToken = awaitToken(inviteeEmail);

        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, inviteeEmail);

        ResponseEntity<String> resent = client().post()
                .uri("/api/organizations/{orgId}/members/{memberId}/resend", orgId, memberId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(resent.getStatusCode()).isEqualTo(HttpStatus.OK);

        // exactly one PENDING invitation, and exactly one SUPERSEDED
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.invitations WHERE member_id = ?::uuid AND status = 'PENDING'",
                Integer.class, memberId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.invitations WHERE member_id = ?::uuid AND status = 'SUPERSEDED'",
                Integer.class, memberId)).isEqualTo(1);

        // the old token no longer resolves to a pending invitation
        ResponseEntity<String> oldDetails = getByToken(firstToken);
        assertThat(oldDetails.getBody()).contains("\"status\":\"SUPERSEDED\"");
    }

    @Test
    @DisplayName("a regular member cannot resend an invitation")
    void resend_requires_admin() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID orgId = createOrg(suffix);
        String inviteeEmail = "invitee-" + suffix + "@example.com";
        assertThat(invite(orgId, inviteeEmail, "Invitee").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String memberId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, orgId, inviteeEmail);

        String strangerUserId = UUID.randomUUID().toString();
        ResponseEntity<String> forbidden = client().post()
                .uri("/api/organizations/{orgId}/members/{memberId}/resend", orgId, memberId)
                .header("Authorization", TestJwtFactory.bearer(strangerUserId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private UUID createOrg(String suffix) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, "acme-" + suffix));
    }

    private UUID createProject(UUID orgId, String slug, String projectName) {
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

    private String createRole(UUID orgId, UUID projectId, String name, List<String> permissions, String schema) {
        ResponseEntity<String> response = client().post().uri("/api/organizations/{orgId}/projects/{projectId}/roles", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "permissions", permissions))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".project_roles WHERE project_id = ?::uuid AND name = ?",
                String.class, projectId, name);
    }

    private ResponseEntity<String> invite(UUID orgId, String email, String displayName) {
        return client().post().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "displayName", displayName, "role", "MEMBER"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> getByToken(String token) {
        return client().get().uri("/api/invitations/{token}", token)
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> accept(String token, UUID callerUserId, UUID orgId) {
        return client().post().uri("/api/invitations/accept")
                .header("Authorization", TestJwtFactory.bearer(callerUserId.toString(), orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", token))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private String memberStatus(String memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM public.members WHERE id = ?::uuid", String.class, memberId);
    }

    /** Seeds a verified account + user in the public registry and returns the user id (JWT sub). */
    private UUID seedUser(String email) {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO public.accounts (id, email, password_hash, status, created_at, updated_at) "
                        + "VALUES (?::uuid, ?, ?, 'ACTIVE', ?, ?)",
                accountId, email, "x", java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        jdbcTemplate.update(
                "INSERT INTO public.users (id, account_id, first_name, last_name, created_at, updated_at) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?)",
                userId, accountId, "First", "Last", java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return userId;
    }

    /** The invitation email is sent asynchronously after commit; poll the capture briefly for the token. */
    private String awaitToken(String email) {
        for (int i = 0; i < 100; i++) {
            String token = tokenCapture.tokenFor(email);
            if (token != null) {
                return token;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Invitation token was not captured for " + email);
    }
}
