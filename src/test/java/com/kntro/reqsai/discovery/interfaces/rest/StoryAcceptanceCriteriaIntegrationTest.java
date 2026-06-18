package com.kntro.reqsai.discovery.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubEmbeddingConfig.class)
@Tag("integration")
@DisplayName("Integration: Story Acceptance Criteria CRUD")
class StoryAcceptanceCriteriaIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final Map<String, String> STORY_BODY = Map.of(
            "title", "Import features", "role", "user",
            "action", "click import", "benefit", "see data", "priority", "MEDIUM");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should perform full CRUD lifecycle for acceptance criteria in tenant schema")
    void should_perform_crud_lifecycle() throws Exception {
        // 1. Arrange - org + tenant
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        String orgId = createOrg(suffix, slug);
        UUID projectId = UUID.randomUUID();

        // 2. Arrange - Create story
        ResponseEntity<String> storyRes = postStory(orgId, projectId, STORY_BODY);
        assertThat(storyRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> storyMap = objectMapper.readValue(storyRes.getBody(), Map.class);
        String storyId = (String) storyMap.get("id");
        assertThat(storyId).isNotNull();

        // 3. Act & Assert - Create Criterion (POST)
        Map<String, String> addRequest = Map.of(
                "scenario", "happy path",
                "given", "the user is on import page",
                "when", "the user clicks import",
                "then", "data is imported"
        );
        ResponseEntity<String> addRes = postCriterion(orgId, projectId, storyId, addRequest);
        assertThat(addRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addRes.getHeaders().getLocation()).isNotNull();

        Map<?, ?> criterionMap = objectMapper.readValue(addRes.getBody(), Map.class);
        String criterionId = (String) criterionMap.get("id");
        assertThat(criterionId).isNotNull();
        assertThat(criterionMap.get("scenario")).isEqualTo("happy path");
        assertThat(criterionMap.get("given")).isEqualTo("the user is on import page");

        // Verify count in tenant database table
        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"tenant_" + slug + "\".acceptance_criteria WHERE story_id = ?::uuid",
                Integer.class, storyId);
        assertThat(countBefore).isEqualTo(1);

        // 4. Act & Assert - Update Criterion (PUT)
        Map<String, String> updateRequest = Map.of(
                "scenario", "updated scenario",
                "given", "updated given",
                "when", "updated when",
                "then", "updated then"
        );
        ResponseEntity<String> updateRes = putCriterion(orgId, projectId, storyId, criterionId, updateRequest);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> updatedMap = objectMapper.readValue(updateRes.getBody(), Map.class);
        assertThat(updatedMap.get("scenario")).isEqualTo("updated scenario");
        assertThat(updatedMap.get("given")).isEqualTo("updated given");

        // 5. Act & Assert - Delete Criterion (DELETE)
        ResponseEntity<Void> deleteRes = deleteCriterion(orgId, projectId, storyId, criterionId);
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify count in tenant database table is 0
        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"tenant_" + slug + "\".acceptance_criteria WHERE story_id = ?::uuid",
                Integer.class, storyId);
        assertThat(countAfter).isEqualTo(0);

        // Verify trying to update deleted criterion fails with 404
        ResponseEntity<String> updateDeletedRes = putCriterion(orgId, projectId, storyId, criterionId, updateRequest);
        assertThat(updateDeletedRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should reject creating a criterion with invalid input")
    void should_reject_invalid_creation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(suffix, "acme-" + suffix);
        UUID projectId = UUID.randomUUID();

        ResponseEntity<String> storyRes = postStory(orgId, projectId, STORY_BODY);
        Map<?, ?> storyMap = objectMapper.readValue(storyRes.getBody(), Map.class);
        String storyId = (String) storyMap.get("id");

        // given/when/then blank
        Map<String, String> invalidRequest = Map.of(
                "scenario", "happy path",
                "given", "  ",
                "when", "when text",
                "then", "then text"
        );
        ResponseEntity<String> addRes = postCriterion(orgId, projectId, storyId, invalidRequest);
        assertThat(addRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String createOrg(String suffix, String expectedSlug) {
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
    }

    private ResponseEntity<String> postStory(String orgId, UUID projectId, Map<String, String> body) {
        return client().post().uri("/api/projects/{p}/stories", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
     }

    private ResponseEntity<String> postCriterion(String orgId, UUID projectId, String storyId, Map<String, String> body) {
        return client().post().uri("/api/projects/{p}/stories/{s}/criteria", projectId, storyId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<String> putCriterion(String orgId, UUID projectId, String storyId, String criterionId, Map<String, String> body) {
        return client().put().uri("/api/projects/{p}/stories/{s}/criteria/{c}", projectId, storyId, criterionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<Void> deleteCriterion(String orgId, UUID projectId, String storyId, String criterionId) {
        return client().delete().uri("/api/projects/{p}/stories/{s}/criteria/{c}", projectId, storyId, criterionId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode()).build(), false);
    }
}
