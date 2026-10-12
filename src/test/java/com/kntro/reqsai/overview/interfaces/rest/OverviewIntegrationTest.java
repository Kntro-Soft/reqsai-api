package com.kntro.reqsai.overview.interfaces.rest;

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
 * The project and organization dashboards end to end over HTTP and the tenant schema: the figures are SQL
 * aggregates of rows seeded straight into the tenant tables, each project section follows the caller's project
 * permissions, and the organization page covers only the projects the caller may reach, never another tenant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: project and organization overview")
class OverviewIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String READER_USER_ID = "00000000-0000-0000-0000-000000000007";
    private static final String OUTSIDER_USER_ID = "00000000-0000-0000-0000-000000000008";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("project overview counts in SQL and shows each caller only the sections they may read")
    void projectOverview() throws Exception {
        Fixture f = fixture();

        JsonNode owner = body(get(OWNER_USER_ID, f.orgId, "/api/projects/" + f.alpha + "/overview"));
        assertThat(owner.path("weeks").asInt()).isEqualTo(12);

        JsonNode backlog = owner.path("backlog");
        assertThat(backlog.path("total").asLong()).isEqualTo(5);
        assertThat(backlog.path("byStatus").path("DRAFT").asLong()).isEqualTo(2);
        assertThat(backlog.path("byStatus").path("APPROVED").asLong()).isEqualTo(1);
        assertThat(backlog.path("byStatus").path("REJECTED").asLong()).isEqualTo(1);
        assertThat(backlog.path("byStatus").path("EXPORTED").asLong()).isEqualTo(1);
        assertThat(backlog.path("byStatus").path("MERGED").asLong()).isZero();
        assertThat(backlog.path("byPriority").path("CRITICAL").asLong()).isEqualTo(1);
        assertThat(backlog.path("byPriority").path("LOW").asLong()).isEqualTo(1);
        // Live stories: 2 drafts + approved + exported = 4; the rejected one (8 points) stays out of the points.
        assertThat(backlog.path("storyPoints").path("total").asLong()).isEqualTo(3 + 8 + 13);
        assertThat(backlog.path("storyPoints").path("approved").asLong()).isEqualTo(8 + 13);
        assertThat(backlog.path("storyPoints").path("unestimated").asLong()).isEqualTo(1);
        assertThat(backlog.path("coverage").path("live").asLong()).isEqualTo(4);
        assertThat(backlog.path("coverage").path("withAcceptanceCriteria").asLong()).isEqualTo(1);
        assertThat(backlog.path("coverage").path("withOrigin").asLong()).isEqualTo(2);
        assertThat(backlog.path("coverage").path("linkedToCode").asLong()).isEqualTo(1);
        assertThat(backlog.path("createdByWeek")).hasSize(12);
        assertThat(sum(backlog.path("createdByWeek"))).isEqualTo(5);
        assertThat(backlog.path("recent")).hasSize(5);
        assertThat(backlog.path("recent").get(0).path("projectId").asString()).isEqualTo(f.alpha.toString());

        JsonNode discovery = owner.path("discovery");
        assertThat(discovery.path("sessions").asLong()).isEqualTo(3);
        assertThat(discovery.path("byStatus").path("COMPLETED").asLong()).isEqualTo(3);
        assertThat(discovery.path("byStatus").path("RECORDING").asLong()).isZero();
        assertThat(discovery.path("createdByWeek")).hasSize(12);
        // The session from 20 weeks ago falls outside the 12-week window.
        assertThat(sum(discovery.path("createdByWeek"))).isEqualTo(2);
        JsonNode assistance = discovery.path("assistance");
        assertThat(assistance.path("accepted").asLong()).isEqualTo(2);
        assertThat(assistance.path("dismissed").asLong()).isEqualTo(1);
        assertThat(assistance.path("pending").asLong()).isEqualTo(1);
        assertThat(assistance.path("questions").asLong()).isEqualTo(1);
        assertThat(assistance.path("acceptanceRate").asDouble()).isEqualTo(2.0 / 3.0);
        assertThat(discovery.path("recent")).hasSize(3);

        JsonNode knowledge = owner.path("knowledge");
        assertThat(knowledge.path("glossaryTerms").asLong()).isEqualTo(2);
        assertThat(knowledge.path("constraints").asLong()).isEqualTo(1);
        assertThat(knowledge.path("documents").asLong()).isEqualTo(1);
        assertThat(knowledge.path("assignedMembers").asLong()).isEqualTo(1);

        JsonNode code = owner.path("code");
        assertThat(code.path("repositories").asLong()).isEqualTo(2);
        assertThat(code.path("ready").asLong()).isEqualTo(1);
        assertThat(code.path("failed").asLong()).isEqualTo(1);
        assertThat(code.path("modules").asLong()).isEqualTo(2);
        assertThat(code.path("files").asLong()).isEqualTo(40);

        // A reader sees only what their role reads: stories, sessions and the glossary, no code, no constraints.
        JsonNode reader = body(get(READER_USER_ID, f.orgId, "/api/projects/" + f.alpha + "/overview"));
        assertThat(reader.path("backlog").path("total").asLong()).isEqualTo(5);
        assertThat(reader.path("discovery").path("sessions").asLong()).isEqualTo(3);
        assertThat(reader.path("knowledge").path("glossaryTerms").asLong()).isEqualTo(2);
        assertThat(reader.path("knowledge").path("constraints").isNull()).isTrue();
        assertThat(reader.path("knowledge").path("documents").isNull()).isTrue();
        assertThat(reader.path("code").isNull()).isTrue();

        // Someone with no access to the project is turned away; a project that does not exist is a 404.
        ResponseEntity<String> denied = get(OUTSIDER_USER_ID, f.orgId, "/api/projects/" + f.alpha + "/overview");
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(OWNER_USER_ID, f.orgId, "/api/projects/" + UUID.randomUUID() + "/overview").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rawGet("/api/projects/" + f.alpha + "/overview", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("organization overview aggregates the projects the caller may reach and hides the roster")
    void organizationOverview() throws Exception {
        Fixture f = fixture();

        JsonNode owner = body(get(OWNER_USER_ID, f.orgId, "/api/organizations/" + f.orgId + "/overview"));
        assertThat(owner.path("projects").asInt()).isEqualTo(2);
        assertThat(owner.path("members").path("active").asLong()).isEqualTo(3);
        assertThat(owner.path("members").path("pending").asLong()).isZero();
        assertThat(owner.path("members").path("byRole").path("OWNER").asLong()).isEqualTo(1);
        assertThat(owner.path("members").path("byRole").path("MEMBER").asLong()).isEqualTo(2);
        assertThat(owner.path("backlog").path("total").asLong()).isEqualTo(6);
        assertThat(owner.path("backlog").path("recent")).isEmpty();
        assertThat(owner.path("discovery").path("sessions").asLong()).isEqualTo(4);
        assertThat(owner.path("discovery").path("assistance").path("accepted").asLong()).isEqualTo(2);
        assertThat(owner.path("code").path("repositories").asLong()).isEqualTo(2);
        JsonNode top = owner.path("topProjects");
        assertThat(top).hasSize(2);
        assertThat(top.get(0).path("projectId").asString()).isEqualTo(f.alpha.toString());
        assertThat(top.get(0).path("stories").asLong()).isEqualTo(5);
        assertThat(top.get(0).path("sessions").asLong()).isEqualTo(3);
        assertThat(top.get(1).path("projectId").asString()).isEqualTo(f.beta.toString());

        // The reader reaches one project only and is not shown the roster.
        JsonNode reader = body(get(READER_USER_ID, f.orgId, "/api/organizations/" + f.orgId + "/overview"));
        assertThat(reader.path("projects").asInt()).isEqualTo(1);
        assertThat(reader.path("members").isNull()).isTrue();
        assertThat(reader.path("backlog").path("total").asLong()).isEqualTo(5);
        assertThat(reader.path("topProjects")).hasSize(1);

        // Somebody who reaches no project gets an empty, well-formed overview rather than an error.
        JsonNode outsider = body(get(OUTSIDER_USER_ID, f.orgId, "/api/organizations/" + f.orgId + "/overview"));
        assertThat(outsider.path("projects").asInt()).isZero();
        assertThat(outsider.path("backlog").path("total").asLong()).isZero();
        assertThat(outsider.path("backlog").path("byStatus").path("DRAFT").asLong()).isZero();
        assertThat(outsider.path("discovery").path("createdByWeek")).hasSize(12);
        assertThat(outsider.path("topProjects")).isEmpty();
        assertThat(outsider.path("code").path("repositories").asLong()).isZero();
    }

    @Test
    @DisplayName("organization overview refuses an organization the token is not bound to, and non-members")
    void organizationOverviewIsTenantBound() {
        Fixture f = fixture();
        UUID otherOrg = createOrganization(UUID.randomUUID().toString().substring(0, 8)).orgId;

        // The caller owns both organizations, but the token is bound to the first one.
        ResponseEntity<String> mismatch = get(OWNER_USER_ID, f.orgId, "/api/organizations/" + otherOrg + "/overview");
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(mismatch.getBody()).contains("OVERVIEW_ORGANIZATION_MISMATCH");

        String stranger = "00000000-0000-0000-0000-0000000000ff";
        assertThat(get(stranger, f.orgId, "/api/organizations/" + f.orgId + "/overview").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a brand-new project has well-formed, empty figures")
    void emptyProject() throws Exception {
        Fixture f = createOrganization(UUID.randomUUID().toString().substring(0, 8));
        UUID project = createProject(f, "Vacío");

        JsonNode overview = body(get(OWNER_USER_ID, f.orgId, "/api/projects/" + project + "/overview"));
        assertThat(overview.path("backlog").path("total").asLong()).isZero();
        assertThat(overview.path("backlog").path("byStatus").path("APPROVED").asLong()).isZero();
        assertThat(overview.path("backlog").path("createdByWeek")).hasSize(12);
        assertThat(overview.path("discovery").path("assistance").path("acceptanceRate").isNull()).isTrue();
        assertThat(overview.path("knowledge").path("glossaryTerms").asLong()).isZero();
        assertThat(overview.path("code").path("repositories").asLong()).isZero();
    }

    private record Fixture(UUID orgId, String slug, UUID alpha, UUID beta) {
        String schema() {
            return "tenant_" + slug;
        }
    }

    /**
     * An organization with an owner, a reader assigned to "Alpha" (stories, sessions, glossary) and an unassigned
     * outsider, plus two projects seeded with stories, sessions, suggestions, glossary, constraints, a document
     * and connected code.
     */
    private Fixture fixture() {
        Fixture base = createOrganization(UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("UPDATE public.organizations SET member_base_permission = 'NONE' WHERE id = ?", base.orgId);
        createMember(base.orgId, READER_USER_ID, "reader@example.com");
        createMember(base.orgId, OUTSIDER_USER_ID, "outsider@example.com");

        archiveDemoProject(base);
        UUID alpha = createProject(base, "Alpha");
        UUID beta = createProject(base, "Beta");
        Fixture f = new Fixture(base.orgId, base.slug, alpha, beta);

        String roleId = createRole(f, alpha, "Reader", List.of("STORY_READ", "SESSION_READ", "GLOSSARY_READ"));
        UUID readerMember = UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM public.members WHERE organization_id = ?::uuid AND email = ?",
                String.class, f.orgId.toString(), "reader@example.com"));
        ResponseEntity<String> assigned = post(OWNER_USER_ID, f.orgId,
                "/api/organizations/" + f.orgId + "/projects/" + alpha + "/members",
                Map.of("memberId", readerMember.toString(), "roleId", roleId));
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        seed(f);
        return f;
    }

    /**
     * A new organization is given a sample project asynchronously. Archive it once it shows up so the figures
     * asserted here are only those seeded below (and so archived projects are shown to stay out of the overview).
     */
    private void archiveDemoProject(Fixture f) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            int archived = jdbc.update("UPDATE \"" + f.schema() + "\".projects SET status = 'ARCHIVED' WHERE demo");
            if (archived > 0) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void seed(Fixture f) {
        String s = "\"" + f.schema() + "\".";
        UUID approved = story(s, f.alpha, "Approved story", "APPROVED", "HIGH", 8, "cita");
        story(s, f.alpha, "Draft story", "DRAFT", "MEDIUM", 3, null);
        story(s, f.alpha, "Draft without points", "DRAFT", "LOW", null, "cita");
        story(s, f.alpha, "Rejected story", "REJECTED", "CRITICAL", 8, "cita");
        story(s, f.alpha, "Exported story", "EXPORTED", "MEDIUM", 13, null);
        jdbc.update("INSERT INTO " + s + "acceptance_criteria (story_id, given_text, when_text, then_text)"
                + " VALUES (?, 'dado', 'cuando', 'entonces')", approved);
        jdbc.update("UPDATE " + s + "user_stories SET code_refs = '[{\"path\":\"src/a\"}]'::jsonb WHERE id = ?",
                approved);
        story(s, f.beta, "Beta story", "DRAFT", "LOW", 1, null);

        UUID session = session(s, f.alpha, "Hoy", "COMPLETED", "now()");
        session(s, f.alpha, "Hace tres semanas", "COMPLETED", "now() - interval '21 days'");
        session(s, f.alpha, "Hace veinte semanas", "COMPLETED", "now() - interval '140 days'");
        session(s, f.beta, "Beta", "COMPLETED", "now()");

        suggestion(s, session, f.alpha, "NEW_STORY", "ACCEPTED");
        suggestion(s, session, f.alpha, "EDGE_CASE", "ACCEPTED");
        suggestion(s, session, f.alpha, "NEW_STORY", "DISMISSED");
        suggestion(s, session, f.alpha, "UPDATE_STORY", "PENDING");
        suggestion(s, session, f.alpha, "CLARIFYING_QUESTION", "ACCEPTED");

        jdbc.update("INSERT INTO " + s + "glossaries (id, project_id) VALUES (?, ?) ON CONFLICT (project_id) DO NOTHING",
                UUID.randomUUID(), f.alpha);
        UUID glossaryId = jdbc.queryForObject("SELECT id FROM " + s + "glossaries WHERE project_id = ?", UUID.class,
                f.alpha);
        for (String term : List.of("Comanda", "Mesa")) {
            jdbc.update("INSERT INTO " + s + "glossary_terms (id, glossary_id, term, definition, added_by, added_at)"
                    + " VALUES (?, ?, ?, 'definición', ?, now())", UUID.randomUUID(), glossaryId, term,
                    UUID.fromString(OWNER_USER_ID));
        }
        jdbc.update("INSERT INTO " + s + "project_constraints (id, project_id, description) VALUES (?, ?, 'Solo web')",
                UUID.randomUUID(), f.alpha);
        jdbc.update("INSERT INTO " + s + "project_documents (id, project_id, name, document_type, status)"
                + " VALUES (?, ?, 'Contrato', 'OTHER', 'ACTIVE')", UUID.randomUUID(), f.alpha);
        jdbc.update("INSERT INTO " + s + "project_documents (id, project_id, name, document_type, status)"
                + " VALUES (?, ?, 'Borrador', 'OTHER', 'ARCHIVED')", UUID.randomUUID(), f.alpha);

        UUID ready = repository(s, f.alpha, "reservas", "READY", 25, "now()");
        repository(s, f.alpha, "pagos", "FAILED", 15, "NULL");
        module(s, ready, f.alpha, "src/reservas");
        module(s, ready, f.alpha, "src/mesas");
    }

    private UUID story(String s, UUID project, String title, String status, String priority, Integer points,
                       String quote) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + s + "user_stories (id, project_id, title, role, action, benefit, priority,"
                + " story_points, status, origin_quote, created_at, updated_at)"
                + " VALUES (?, ?, ?, 'comensal', 'reservar', 'comer', ?, ?, ?, ?, now(), now())",
                id, project, title, priority, points, status, quote);
        return id;
    }

    private UUID session(String s, UUID project, String title, String status, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + s + "discovery_sessions (id, project_id, title, language, status, created_at,"
                + " updated_at) VALUES (?, ?, ?, 'es-PE', ?, " + createdAt + ", " + createdAt + ")",
                id, project, title, status);
        return id;
    }

    private void suggestion(String s, UUID session, UUID project, String type, String status) {
        jdbc.update("INSERT INTO " + s + "suggestions (id, session_id, project_id, type, status, created_at,"
                + " updated_at) VALUES (?, ?, ?, ?, ?, now(), now())",
                UUID.randomUUID(), session, project, type, status);
    }

    private UUID repository(String s, UUID project, String name, String status, int files, String indexedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + s + "code_repositories (id, project_id, provider, owner, name, branch,"
                + " html_url, status, file_count, indexed_at, created_at, updated_at)"
                + " VALUES (?, ?, 'GITHUB', 'acme', ?, 'main', 'https://github.com/acme/x', ?, ?, " + indexedAt
                + ", now(), now())", id, project, name, status, files);
        return id;
    }

    private void module(String s, UUID repository, UUID project, String path) {
        jdbc.update("INSERT INTO " + s + "code_modules (id, repository_id, project_id, path, name, summary,"
                + " file_count, content_hash, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, 'resumen', 3, 'hash', now(), now())",
                UUID.randomUUID(), repository, project, path, path);
    }

    private static double sum(JsonNode weeks) {
        double total = 0;
        for (JsonNode week : weeks) {
            total += week.path("count").asLong();
        }
        return total;
    }

    private static JsonNode body(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(response.getBody());
    }

    private Fixture createOrganization(String suffix) {
        String slug = "acme-" + suffix;
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(OWNER_USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orgId = UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, slug));
        return new Fixture(orgId, slug, null, null);
    }

    private void createMember(UUID orgId, String userId, String email) {
        ResponseEntity<String> res = post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members",
                Map.of("userId", userId, "email", email, "displayName", email, "role", "MEMBER"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private UUID createProject(Fixture f, String name) {
        ResponseEntity<String> res = post(OWNER_USER_ID, f.orgId, "/api/organizations/" + f.orgId + "/projects",
                Map.of("name", name,
                        "description", "Proyecto",
                        "programmingLanguages", List.of("Java"),
                        "frameworks", List.of("Spring Boot"),
                        "clientPlatforms", List.of("Web"),
                        "databases", List.of("PostgreSQL"),
                        "architecture", "Clean Architecture",
                        "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM \"" + f.schema() + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, f.orgId.toString(), name));
    }

    private String createRole(Fixture f, UUID project, String name, List<String> permissions) {
        ResponseEntity<String> res = post(OWNER_USER_ID, f.orgId,
                "/api/organizations/" + f.orgId + "/projects/" + project + "/roles",
                Map.of("name", name, "permissions", permissions));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbc.queryForObject("SELECT id::text FROM \"" + f.schema() + "\".project_roles WHERE project_id = ?::uuid"
                + " AND name = ?", String.class, project, name);
    }

    private ResponseEntity<String> get(String userId, UUID orgId, String uri) {
        return rawGet(uri, TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"));
    }

    private ResponseEntity<String> rawGet(String uri, String authorization) {
        var spec = client().get().uri(uri).header("Api-Version", "1");
        if (authorization != null) {
            spec = spec.header("Authorization", authorization);
        }
        return spec.exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> post(String userId, UUID orgId, String uri, Map<String, Object> body) {
        return client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }
}
