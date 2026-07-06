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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the session-control hardening: single active session per project,
 * post-stop suggestion decisions, project-level pending suggestions, and session stats.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Discovery Session Control")
class DiscoverySessionControlIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should reject starting a second session while another is active (409 SESSION_ALREADY_ACTIVE)")
    void should_reject_second_active_session() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();

        UUID first = sessionId(postSession(orgId, projectId, "First"));
        UUID second = sessionId(postSession(orgId, projectId, "Second"));

        assertThat(start(orgId, projectId, first).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> conflict = start(orgId, projectId, second);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("SESSION_ALREADY_ACTIVE");
        assertThat(conflict.getBody()).contains(first.toString());

        // After stopping the first, the second can start.
        assertThat(stop(orgId, projectId, first).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(start(orgId, projectId, second).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("should allow accepting a PENDING suggestion after the session has stopped")
    void should_decide_suggestion_after_stop() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String schema = "tenant_acme-" + suffix;
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionId = sessionId(postSession(orgId, projectId, "Meeting"));

        // Drive the session to STOPPED.
        assertThat(start(orgId, projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stop(orgId, projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Seed a PENDING NEW_STORY suggestion directly into the tenant schema.
        UUID suggestionId = UUID.randomUUID();
        insertPendingNewStory(schema, suggestionId, sessionId, projectId);

        // Post-stop acceptance must succeed and produce a story.
        ResponseEntity<String> accepted = client().post()
                .uri("/api/sessions/{s}/suggestions/{id}/accept", sessionId, suggestionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody()).contains("\"status\":\"ACCEPTED\"");

        Integer stories = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".user_stories WHERE session_id = ?::uuid",
                Integer.class, sessionId.toString());
        assertThat(stories).isEqualTo(1);
    }

    @Test
    @DisplayName("should list a project's PENDING suggestions across sessions")
    void should_list_project_pending_suggestions() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String schema = "tenant_acme-" + suffix;
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionA = sessionId(postSession(orgId, projectId, "A"));
        UUID sessionB = sessionId(postSession(orgId, projectId, "B"));

        insertPendingNewStory(schema, UUID.randomUUID(), sessionA, projectId);
        insertPendingNewStory(schema, UUID.randomUUID(), sessionB, projectId);

        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/suggestions?status=PENDING", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"totalElements\":2");
    }

    @Test
    @DisplayName("should expose session stats and durationSeconds on the get endpoint")
    void should_expose_stats() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String schema = "tenant_acme-" + suffix;
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        UUID sessionId = sessionId(postSession(orgId, projectId, "Stats"));

        assertThat(start(orgId, projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stop(orgId, projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);

        // One accepted (APPROVED) story + one pending suggestion + one clarifying question.
        insertStory(schema, UUID.randomUUID(), sessionId, projectId, "APPROVED");
        insertPendingNewStory(schema, UUID.randomUUID(), sessionId, projectId);
        insertPendingQuestion(schema, UUID.randomUUID(), sessionId, projectId);

        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/sessions/{s}", projectId, sessionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = res.getBody();
        assertThat(body).contains("\"storiesGenerated\":1");
        assertThat(body).contains("\"storiesAccepted\":1");
        assertThat(body).contains("\"suggestionsPending\":2");
        assertThat(body).contains("\"questionsAsked\":1");
        assertThat(body).contains("\"durationSeconds\":");
    }

    // ----- helpers -----

    private String createOrg(String suffix) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, "acme-" + suffix);
    }

    private ResponseEntity<String> postSession(String orgId, UUID projectId, String title) {
        return client().post().uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title, "language", "es-PE"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }

    private UUID sessionId(ResponseEntity<String> created) {
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = created.getBody();
        assertThat(body).isNotNull();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private ResponseEntity<String> start(String orgId, UUID projectId, UUID sessionId) {
        return post(orgId, "/api/projects/" + projectId + "/sessions/" + sessionId + "/start");
    }

    private ResponseEntity<String> stop(String orgId, UUID projectId, UUID sessionId) {
        return post(orgId, "/api/projects/" + projectId + "/sessions/" + sessionId + "/stop");
    }

    private ResponseEntity<String> post(String orgId, String uri) {
        return client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }

    private void insertPendingNewStory(String schema, UUID id, UUID sessionId, UUID projectId) {
        jdbcTemplate.update(
                "INSERT INTO \"" + schema + "\".suggestions "
                        + "(id, session_id, project_id, type, status, draft_title, draft_role, draft_action, draft_benefit, draft_priority, created_at, updated_at) "
                        + "VALUES (?::uuid, ?::uuid, ?::uuid, 'NEW_STORY', 'PENDING', 'Login', 'user', 'log in', 'access', 'HIGH', ?, ?)",
                id.toString(), sessionId.toString(), projectId.toString(), now(), now());
    }

    private void insertPendingQuestion(String schema, UUID id, UUID sessionId, UUID projectId) {
        jdbcTemplate.update(
                "INSERT INTO \"" + schema + "\".suggestions "
                        + "(id, session_id, project_id, type, status, question, created_at, updated_at) "
                        + "VALUES (?::uuid, ?::uuid, ?::uuid, 'CLARIFYING_QUESTION', 'PENDING', 'What auth provider?', ?, ?)",
                id.toString(), sessionId.toString(), projectId.toString(), now(), now());
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private void insertStory(String schema, UUID id, UUID sessionId, UUID projectId, String status) {
        jdbcTemplate.update(
                "INSERT INTO \"" + schema + "\".user_stories "
                        + "(id, session_id, project_id, title, role, action, benefit, priority, status, created_at, updated_at) "
                        + "VALUES (?::uuid, ?::uuid, ?::uuid, 'Login', 'user', 'log in', 'access', 'HIGH', ?, ?, ?)",
                id.toString(), sessionId.toString(), projectId.toString(), status, now(), now());
    }
}
