package com.kntro.reqsai.discovery.interfaces.rest;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for GET /api/projects/{projectId}/stories and GET /{storyId}.
 * Creates a tenant org, stories, then exercises the read endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubEmbeddingConfig.class)
@Tag("integration")
@DisplayName("Integration: Get Project Stories")
class GetProjectStoriesIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final Map<String, String> STORY_A = Map.of(
            "title", "Upload CSV", "role", "analyst",
            "action", "upload a CSV of suppliers", "benefit", "save time", "priority", "HIGH");
    private static final Map<String, String> STORY_B = Map.of(
            "title", "Export report", "role", "manager",
            "action", "export a monthly report", "benefit", "share with stakeholders", "priority", "MEDIUM");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should return a story by id")
    void should_return_story_by_id() {
        // Arrange
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        ResponseEntity<String> created = postStory(orgId, projectId, STORY_A);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String location = created.getHeaders().getFirst("Location");
        assertThat(location).isNotNull();
        String storyId = location.substring(location.lastIndexOf('/') + 1);

        // Act
        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/stories/{s}", projectId, storyId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"id\":\"" + storyId + "\"");
        assertThat(res.getBody()).contains("\"title\":\"Upload CSV\"");
        assertThat(res.getBody()).contains("\"priority\":\"HIGH\"");
        assertThat(res.getBody()).contains("\"embeddingIndexed\":true");
    }

    @Test
    @DisplayName("should return 404 for an unknown story id")
    void should_return_404_for_unknown_story() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);

        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/stories/{s}", UUID.randomUUID(), UUID.randomUUID())
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("USER_STORY_NOT_FOUND");
    }

    @Test
    @DisplayName("should list all stories for a project")
    void should_list_stories_for_project() {
        // Arrange
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        assertThat(postStory(orgId, projectId, STORY_A).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postStory(orgId, projectId, STORY_B).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Act
        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/stories", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"totalElements\":2");
        assertThat(res.getBody()).contains("Upload CSV");
        assertThat(res.getBody()).contains("Export report");
    }

    @Test
    @DisplayName("should filter the backlog by search across title/role/action")
    void should_filter_by_search() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        assertThat(postStory(orgId, projectId, STORY_A).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postStory(orgId, projectId, STORY_B).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // "upload" appears in STORY_A's action only
        ResponseEntity<String> res = getStories(orgId, projectId, "?search=upload");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"totalElements\":1");
        assertThat(res.getBody()).contains("Upload CSV");
        assertThat(res.getBody()).doesNotContain("Export report");
    }

    @Test
    @DisplayName("should filter the backlog by priority")
    void should_filter_by_priority() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        assertThat(postStory(orgId, projectId, STORY_A).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postStory(orgId, projectId, STORY_B).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> res = getStories(orgId, projectId, "?priority=MEDIUM");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"totalElements\":1");
        assertThat(res.getBody()).contains("Export report");
        assertThat(res.getBody()).doesNotContain("Upload CSV");
    }

    @Test
    @DisplayName("should 400 on an invalid status filter value")
    void should_reject_invalid_status_filter() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();

        ResponseEntity<String> res = getStories(orgId, projectId, "?status=NONSENSE");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should update a story's fields via PUT")
    void should_update_story() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);
        UUID projectId = UUID.randomUUID();
        ResponseEntity<String> created = postStory(orgId, projectId, STORY_A);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = created.getHeaders().getFirst("Location");
        assertThat(location).isNotNull();
        String storyId = location.substring(location.lastIndexOf('/') + 1);

        ResponseEntity<String> res = client().put()
                .uri("/api/projects/{p}/stories/{s}", projectId, storyId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Upload XLSX", "role", "analyst",
                        "action", "upload an Excel of suppliers", "benefit", "save even more time",
                        "priority", "CRITICAL", "storyPoints", 8))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"id\":\"" + storyId + "\"");
        assertThat(res.getBody()).contains("\"title\":\"Upload XLSX\"");
        assertThat(res.getBody()).contains("\"priority\":\"CRITICAL\"");
        assertThat(res.getBody()).contains("\"storyPoints\":8");
    }

    @Test
    @DisplayName("should 404 when updating a story that is not in the project")
    void should_reject_update_unknown_story() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix);

        ResponseEntity<String> res = client().put()
                .uri("/api/projects/{p}/stories/{s}", UUID.randomUUID(), UUID.randomUUID())
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "X", "role", "r", "action", "a", "benefit", "b", "priority", "LOW"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("USER_STORY_NOT_FOUND");
    }

    @Test
    @DisplayName("should reject an unauthenticated request on GET /{storyId}")
    void should_reject_unauthenticated_get() {
        ResponseEntity<String> res = client().get()
                .uri("/api/projects/{p}/stories/{s}", UUID.randomUUID(), UUID.randomUUID())
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
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

    private ResponseEntity<String> getStories(String orgId, UUID projectId, String queryString) {
        return client().get()
                .uri("/api/projects/" + projectId + "/stories" + queryString)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<String> postStory(String orgId, UUID projectId, Map<String, String> body) {
        return client().post().uri("/api/projects/{p}/stories", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.bodyTo(String.class)), false);
    }
}
