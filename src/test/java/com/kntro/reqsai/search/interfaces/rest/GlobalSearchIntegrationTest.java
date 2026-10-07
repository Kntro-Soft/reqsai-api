package com.kntro.reqsai.search.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubEmbeddingConfig;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for GET /api/search. Provisions a tenant org (which also creates the owner member),
 * a project and a user story, then exercises the palette search across types (substring, word,
 * accent-insensitive and fuzzy matching).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubEmbeddingConfig.class)
@Tag("integration")
@DisplayName("Integration: Global Search")
class GlobalSearchIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String get(String orgId, String path) {
        return client().get().uri(path)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false)
                .getBody();
    }

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

    private UUID createProject(String orgId, String name) {
        Map<String, Object> request = Map.of(
                "name", name,
                "description", "desc",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Clean Architecture",
                "domain", "Fintech");
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = res.getHeaders().getFirst("Location");
        assertThat(location).isNotNull();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void createStory(String orgId, UUID projectId, String title) {
        ResponseEntity<String> res = client().post().uri("/api/projects/{p}/stories", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title, "role", "user", "action", "do something", "benefit", "value", "priority", "HIGH"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void createGlossaryTerm(String orgId, UUID projectId, String term, String definition) {
        ResponseEntity<String> res = client()
                .post().uri("/api/organizations/{orgId}/projects/{projectId}/glossary/terms", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("term", term, "definition", definition))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void createDocument(String orgId, UUID projectId, String name, String documentType) {
        ResponseEntity<String> res = client()
                .post().uri("/api/organizations/{orgId}/projects/{projectId}/documents", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "documentType", documentType))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("finds a project, its story, the org and the owner member by a shared term")
    void finds_hits_across_types() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = createProject(orgId, "Checkout Redesign");
        createStory(orgId, projectId, "Checkout speed improvements");

        String body = get(orgId, "/api/search?q=checkout&limit=10");

        assertThat(body).contains("\"type\":\"PROJECT\"");
        assertThat(body).contains("Checkout Redesign");
        assertThat(body).contains("\"type\":\"USER_STORY\"");
        assertThat(body).contains("Checkout speed improvements");
        assertThat(body).contains("\"projectId\":\"" + projectId + "\"");
    }

    @Test
    @DisplayName("finds a glossary term and a project document by a shared term")
    void finds_glossary_term_and_document() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = createProject(orgId, "Payments Platform");
        createGlossaryTerm(orgId, projectId, "Settlement", "The final transfer of funds.");
        createDocument(orgId, projectId, "Settlement Runbook", "REFERENCE");

        String body = get(orgId, "/api/search?q=settlement&limit=10");

        assertThat(body).contains("\"type\":\"GLOSSARY_TERM\"");
        assertThat(body).contains("Settlement");
        assertThat(body).contains("The final transfer of funds.");
        assertThat(body).contains("\"type\":\"DOCUMENT\"");
        assertThat(body).contains("Settlement Runbook");
        assertThat(body).contains("\"projectId\":\"" + projectId + "\"");
    }

    @Test
    @DisplayName("does not leak glossary terms or documents from another tenant's projects")
    void excludes_glossary_and_documents_from_inaccessible_projects() {
        String otherSuffix = UUID.randomUUID().toString().substring(0, 8);
        String otherOrgId = createOrg(otherSuffix);
        UUID otherProjectId = createProject(otherOrgId, "Other Platform");
        createGlossaryTerm(otherOrgId, otherProjectId, "Reconciliation", "Matching two sets of records.");
        createDocument(otherOrgId, otherProjectId, "Reconciliation Guide", "REFERENCE");

        // A caller bound to a different tenant must not see the other org's project-scoped hits.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);

        String body = get(orgId, "/api/search?q=reconciliation&limit=10");

        assertThat(body).doesNotContain("\"type\":\"GLOSSARY_TERM\"");
        assertThat(body).doesNotContain("\"type\":\"DOCUMENT\"");
        assertThat(body).doesNotContain(otherProjectId.toString());
    }

    @Test
    @DisplayName("matches the organization by name")
    void finds_organization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);

        // Every org this suite creates is named "Acme <suffix>", and the integration Testcontainer DB is
        // shared with no per-test cleanup. Searching the generic "acme" trigram-matches EVERY such org and,
        // with the default result cap (8), the org just created can fall outside the top hits — a flake that
        // surfaces once enough orgs have accumulated (e.g. in CI). Search the org's UNIQUE suffix so the
        // trigram match is deterministic (still exercises name-based trigram search) regardless of DB volume.
        String body = get(orgId, "/api/search?q=" + suffix);

        assertThat(body).contains("\"type\":\"ORGANIZATION\"");
        assertThat(body).contains("\"id\":\"" + orgId + "\"");
    }

    private void createMember(String orgId, String email, String displayName) {
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/members", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "displayName", displayName, "role", "MEMBER"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("matches a single word inside a long story title, ignoring case")
    void finds_story_by_a_word_inside_a_long_title() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = createProject(orgId, "Bodega Express " + suffix);
        createStory(orgId, projectId, "Costo de delivery según la zona de reparto");
        createStory(orgId, projectId, "Pagar la cita en línea al reservar");

        assertThat(get(orgId, "/api/search?q=Costo&limit=10")).contains("Costo de delivery según la zona de reparto");
        assertThat(get(orgId, "/api/search?q=delivery&limit=10")).contains("Costo de delivery según la zona de reparto");
        assertThat(get(orgId, "/api/search?q=pagar&limit=10")).contains("Pagar la cita en línea al reservar");
    }

    @Test
    @DisplayName("matches names with or without accents")
    void accent_insensitive_matching() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        createProject(orgId, "Clínica Santa Lucía — Portal de citas");

        for (String q : List.of("Clinica", "Clínica", "clinica santa", "LUCIA")) {
            String body = get(orgId, "/api/search?q=" + q + "&limit=10");
            assertThat(body).as("query %s", q).contains("\"type\":\"PROJECT\"");
            assertThat(body).as("query %s", q).contains("Clínica Santa Lucía — Portal de citas");
        }
    }

    @Test
    @DisplayName("finds a member by a first name inside the display name")
    void finds_member_by_first_name() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        createMember(orgId, "valeria." + suffix + "@example.com", "Valeria Ríos Paredes");

        String body = get(orgId, "/api/search?q=valeria&limit=10");

        assertThat(body).contains("\"type\":\"MEMBER\"");
        assertThat(body).contains("Valeria Ríos Paredes");
    }

    @Test
    @DisplayName("keeps a glossary term visible when many stories match the same word")
    void interleaves_types_instead_of_letting_stories_fill_the_cap() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = createProject(orgId, "Portal " + suffix);
        for (int i = 1; i <= 9; i++) {
            createStory(orgId, projectId, "Cita número " + i + " del paciente");
        }
        createGlossaryTerm(orgId, projectId, "Cita", "Reserva de atención con un médico.");

        String body = get(orgId, "/api/search?q=cita&limit=8");

        assertThat(body).contains("\"type\":\"GLOSSARY_TERM\"");
        assertThat(body).contains("\"type\":\"USER_STORY\"");
        long hitCount = body.split("\"type\"", -1).length - 1;
        assertThat(hitCount).isLessThanOrEqualTo(8);
    }

    @Test
    @DisplayName("blank query returns an empty JSON array")
    void blank_query_returns_empty_array() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);

        // Pass the blanks as a URI variable: a literal "%20%20" in the template is re-encoded by the client
        // and reaches the API as the non-blank text "%20%20".
        String body = client().get().uri("/api/search?q={q}", "   ")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false)
                .getBody();

        assertThat(body).isEqualTo("[]");
    }

    @Test
    @DisplayName("caps the number of results to the requested limit")
    void caps_results_to_limit() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        createProject(orgId, "Checkout One");
        createProject(orgId, "Checkout Two");
        createProject(orgId, "Checkout Three");

        String body = get(orgId, "/api/search?q=checkout&limit=2");

        long hitCount = body.split("\"type\"", -1).length - 1;
        assertThat(hitCount).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("rejects an unauthenticated request")
    void rejects_unauthenticated() {
        ResponseEntity<String> res = client().get().uri("/api/search?q=checkout")
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
