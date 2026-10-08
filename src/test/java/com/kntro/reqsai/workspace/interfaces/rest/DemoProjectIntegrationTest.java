package com.kntro.reqsai.workspace.interfaces.rest;

import com.kntro.reqsai.discovery.application.service.DemoDiscoveryContent;
import com.kntro.reqsai.workspace.application.service.DemoProjectTemplate;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end over HTTP and the tenant schema for the demo project (US28): creating an organization seeds
 * the demo with its glossary, constraints, a finished session with transcript, stories and pending
 * suggestions; the demo does not consume the plan's project limit; and "restore demo data" puts back the
 * original content after users changed it, while a regular project refuses with {@code PROJECT_NOT_DEMO}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Demo project")
class DemoProjectIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-000000000009";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a new organization gets a demo project that can be modified and restored")
    void new_organization_gets_a_restorable_demo_project() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        String schema = "\"tenant_" + slug + "\"";

        // The demo exists as soon as the organization is created — seeding is synchronous.
        List<String> demoIds = jdbcTemplate.queryForList(
                "SELECT id::text FROM " + schema + ".projects WHERE organization_id = ?::uuid AND demo",
                String.class, orgId.toString());
        assertThat(demoIds).hasSize(1);
        UUID demoId = UUID.fromString(demoIds.getFirst());
        DemoCounts seeded = counts(schema, demoId);
        assertThat(seeded).isEqualTo(new DemoCounts(
                1, 1, DemoDiscoveryContent.segmentCount(), DemoDiscoveryContent.storyCount(),
                seeded.criteria(), 2, DemoProjectTemplate.glossary().size(),
                DemoProjectTemplate.constraints().size(), 0, 0));
        assertThat(seeded.criteria()).isGreaterThanOrEqualTo(DemoDiscoveryContent.storyCount());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT status) FROM " + schema + ".user_stories WHERE project_id = ?::uuid",
                Integer.class, demoId.toString())).isEqualTo(2);

        // The project list flags it as the demo.
        JsonNode listed = JSON.readTree(get(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects").getBody());
        assertThat(listed.path("content")).hasSize(1);
        assertThat(listed.path("content").get(0).path("demo").asBoolean()).isTrue();
        assertThat(listed.path("content").get(0).path("name").asString()).isEqualTo(DemoProjectTemplate.NAME);

        // The demo does not consume the plan: with a limit of one project, the user still creates their own.
        jdbcTemplate.update("UPDATE public.organizations SET max_projects = 1 WHERE id = ?::uuid", orgId.toString());
        assertThat(createProject(orgId, "Mi proyecto").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> overLimit = createProject(orgId, "Otro proyecto");
        assertThat(overLimit.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(overLimit.getBody()).contains("\"code\":\"PROJECT_PLAN_LIMIT_EXCEEDED\"");
        UUID regularId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM " + schema + ".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Mi proyecto"));

        // Users change the demo: delete a story, add one, add a glossary term, drop a constraint, and a
        // client comments on a story through a share link.
        String storyId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM " + schema + ".user_stories WHERE project_id = ?::uuid ORDER BY title LIMIT 1",
                String.class, demoId.toString());
        String deletedTitle = jdbcTemplate.queryForObject(
                "SELECT title FROM " + schema + ".user_stories WHERE id = ?::uuid", String.class, storyId);
        assertThat(delete(OWNER_USER_ID, orgId, "/api/projects/" + demoId + "/stories/" + storyId).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(OWNER_USER_ID, orgId, "/api/projects/" + demoId + "/stories", Map.of(
                "title", "Pagar la cuenta desde la mesa",
                "role", "comensal",
                "action", "pagar la cuenta escaneando un código QR",
                "benefit", "no esperar al mozo",
                "priority", "LOW",
                "storyPoints", 3)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/projects/" + demoId + "/glossary/terms",
                Map.of("term", "Mozo", "definition", "Persona que atiende las mesas.")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        String constraintId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM " + schema + ".project_constraints WHERE project_id = ?::uuid LIMIT 1",
                String.class, demoId.toString());
        assertThat(delete(OWNER_USER_ID, orgId,
                "/api/organizations/" + orgId + "/projects/" + demoId + "/constraints/" + constraintId).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        String reviewedStoryId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM " + schema + ".user_stories WHERE project_id = ?::uuid LIMIT 1",
                String.class, demoId.toString());
        jdbcTemplate.update("INSERT INTO " + schema + ".story_feedback "
                        + "(id, story_id, share_link_id, kind, author_name, comment, created_at, updated_at) "
                        + "VALUES (?::uuid, ?::uuid, ?::uuid, 'COMMENT', 'Rosa', '¿Y los feriados?', now(), now())",
                UUID.randomUUID().toString(), reviewedStoryId, UUID.randomUUID().toString());
        assertThat(counts(schema, demoId)).isNotEqualTo(seeded);

        // A member with the default READ floor may not restore the demo.
        createMember(orgId);
        assertThat(post(MEMBER_USER_ID, orgId, restorePath(orgId, demoId), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Restoring puts every original record back and drops what the users added.
        ResponseEntity<String> restored = post(OWNER_USER_ID, orgId, restorePath(orgId, demoId), null);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode project = JSON.readTree(restored.getBody());
        assertThat(project.path("id").asString()).isEqualTo(demoId.toString());
        assertThat(project.path("demo").asBoolean()).isTrue();
        assertThat(counts(schema, demoId)).isEqualTo(seeded);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + schema + ".user_stories WHERE project_id = ?::uuid AND title = ?",
                Integer.class, demoId.toString(), deletedTitle)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + schema + ".user_stories WHERE project_id = ?::uuid AND title = ?",
                Integer.class, demoId.toString(), "Pagar la cuenta desde la mesa")).isZero();

        // Restoring twice in a row is harmless.
        assertThat(post(OWNER_USER_ID, orgId, restorePath(orgId, demoId), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(counts(schema, demoId)).isEqualTo(seeded);

        // A regular project refuses the restore with a domain code, and is left untouched.
        ResponseEntity<String> notDemo = post(OWNER_USER_ID, orgId, restorePath(orgId, regularId), null);
        assertThat(notDemo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(notDemo.getBody()).contains("\"code\":\"PROJECT_NOT_DEMO\"");
        assertThat(counts(schema, regularId).stories()).isZero();
    }

    // ----- helpers -----

    private record DemoCounts(int projects, int sessions, int segments, int stories, int criteria,
                              int pendingSuggestions, int glossaryTerms, int constraints, int chatMessages,
                              int clientFeedback) {}

    private DemoCounts counts(String schema, UUID projectId) {
        String id = projectId.toString();
        return new DemoCounts(
                count("SELECT count(*) FROM " + schema + ".projects WHERE id = ?::uuid AND demo", id),
                count("SELECT count(*) FROM " + schema + ".discovery_sessions WHERE project_id = ?::uuid "
                        + "AND status = 'COMPLETED'", id),
                count("SELECT count(*) FROM " + schema + ".transcript_segments t JOIN " + schema
                        + ".discovery_sessions s ON s.id = t.session_id WHERE s.project_id = ?::uuid", id),
                count("SELECT count(*) FROM " + schema + ".user_stories WHERE project_id = ?::uuid", id),
                count("SELECT count(*) FROM " + schema + ".acceptance_criteria c JOIN " + schema
                        + ".user_stories u ON u.id = c.story_id WHERE u.project_id = ?::uuid", id),
                count("SELECT count(*) FROM " + schema + ".suggestions WHERE project_id = ?::uuid "
                        + "AND status = 'PENDING'", id),
                count("SELECT count(*) FROM " + schema + ".glossary_terms t JOIN " + schema
                        + ".glossaries g ON g.id = t.glossary_id WHERE g.project_id = ?::uuid", id),
                count("SELECT count(*) FROM " + schema + ".project_constraints WHERE project_id = ?::uuid", id),
                count("SELECT count(*) FROM " + schema + ".assistant_messages WHERE project_id = ?::uuid", id),
                count("SELECT count(*) FROM " + schema + ".story_feedback f JOIN " + schema
                        + ".user_stories u ON u.id = f.story_id WHERE u.project_id = ?::uuid", id));
    }

    private int count(String sql, String projectId) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, projectId);
        return value == null ? 0 : value;
    }

    private static String restorePath(UUID orgId, UUID projectId) {
        return "/api/organizations/" + orgId + "/projects/" + projectId + "/demo/restore";
    }

    private ResponseEntity<String> get(String userId, UUID orgId, String uri) {
        return client().get().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> delete(String userId, UUID orgId, String uri) {
        return client().delete().uri(uri)
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

    private UUID createOrganization(String suffix, String slug) {
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, slug));
    }

    private ResponseEntity<String> createProject(UUID orgId, String name) {
        return post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects", Map.of(
                "name", name,
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Hexagonal",
                "domain", "Gastronomía"));
    }

    private void createMember(UUID orgId) {
        assertThat(post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com",
                "displayName", "Member", "role", "MEMBER")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }
}
