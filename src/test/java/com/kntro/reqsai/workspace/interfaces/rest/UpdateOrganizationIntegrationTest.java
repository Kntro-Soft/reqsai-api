package com.kntro.reqsai.workspace.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Update Organization")
class UpdateOrganizationIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ORG_ID = "00000000-0000-0000-0000-000000000009";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int serverPort;

    @Test
    @DisplayName("should allow the owner to update organization name and settings")
    void should_allow_owner_to_update_organization_name_and_settings() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String originalName = "Acme " + suffix;
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", originalName, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);

        ResponseEntity<String> updateResponse = patch(
                organizationId,
                Map.of(
                        "name", "Acme International " + suffix,
                        "meetingLanguage", "pt-BR",
                        "audioRetentionDays", -1),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).contains("\"name\":\"Acme International " + suffix + "\"");
        assertThat(updateResponse.getBody()).contains("\"meetingLanguage\":\"pt-BR\"");
        assertThat(updateResponse.getBody()).contains("\"audioRetentionDays\":-1");
        assertThat(updateResponse.getBody()).contains("\"slug\":\"" + expectedSlug + "\"");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, slug, meeting_language, audio_retention_days FROM public.organizations WHERE id = ?::uuid",
                organizationId);
        assertThat(row.get("name")).isEqualTo("Acme International " + suffix);
        assertThat(row.get("slug")).isEqualTo(expectedSlug);
        assertThat(row.get("meeting_language")).isEqualTo("pt-BR");
        assertThat(row.get("audio_retention_days")).isEqualTo(-1);
    }

    @Test
    @DisplayName("should update only the provided field and leave the others unchanged")
    void should_update_only_provided_field() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String originalName = "Acme " + suffix;
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", originalName, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);

        ResponseEntity<String> updateResponse = patch(
                organizationId,
                Map.of("name", "Renamed " + suffix),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).contains("\"name\":\"Renamed " + suffix + "\"");
        // meetingLanguage and audioRetentionDays were omitted, so they keep their original values
        assertThat(updateResponse.getBody()).contains("\"meetingLanguage\":\"en-US\"");
        assertThat(updateResponse.getBody()).contains("\"audioRetentionDays\":30");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, meeting_language, audio_retention_days FROM public.organizations WHERE id = ?::uuid",
                organizationId);
        assertThat(row.get("name")).isEqualTo("Renamed " + suffix);
        assertThat(row.get("meeting_language")).isEqualTo("en-US");
        assertThat(row.get("audio_retention_days")).isEqualTo(30);
    }

    @Test
    @DisplayName("should be a no-op returning 200 with the unchanged organization for an empty body")
    void should_be_a_no_op_for_empty_body() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String originalName = "Acme " + suffix;
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", originalName, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);

        ResponseEntity<String> updateResponse = patch(
                organizationId,
                new HashMap<>(),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).contains("\"name\":\"" + originalName + "\"");
        assertThat(updateResponse.getBody()).contains("\"meetingLanguage\":\"en-US\"");
        assertThat(updateResponse.getBody()).contains("\"audioRetentionDays\":30");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, meeting_language, audio_retention_days FROM public.organizations WHERE id = ?::uuid",
                organizationId);
        assertThat(row.get("name")).isEqualTo(originalName);
        assertThat(row.get("meeting_language")).isEqualTo("en-US");
        assertThat(row.get("audio_retention_days")).isEqualTo(30);
    }

    @Test
    @DisplayName("should reject an invalid value even on a partial update")
    void should_reject_invalid_value_on_partial_update() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String originalName = "Acme " + suffix;
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", originalName, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);

        ResponseEntity<String> updateResponse = patch(
                organizationId,
                Map.of("audioRetentionDays", -2),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT audio_retention_days FROM public.organizations WHERE id = ?::uuid",
                organizationId);
        assertThat(row.get("audio_retention_days")).isEqualTo(30);
    }

    @Test
    @DisplayName("should reject update from a non-owner")
    void should_reject_update_from_non_owner() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String originalName = "Acme " + suffix;
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", originalName, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);

        ResponseEntity<String> updateResponse = patch(
                organizationId,
                Map.of(
                        "name", "Forbidden Update " + suffix,
                        "meetingLanguage", "pt-BR",
                        "audioRetentionDays", 7),
                TestJwtFactory.bearer("00000000-0000-0000-0000-000000000002", ORG_ID, "ROLE_USER"));

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, meeting_language, audio_retention_days FROM public.organizations WHERE id = ?::uuid",
                organizationId);
        assertThat(row.get("name")).isEqualTo(originalName);
        assertThat(row.get("meeting_language")).isEqualTo("en-US");
        assertThat(row.get("audio_retention_days")).isEqualTo(30);
    }

    @Test
    @DisplayName("should reject unauthenticated update request")
    void should_reject_unauthenticated_update_request() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", "Acme " + suffix, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);

        ResponseEntity<String> updateResponse = patchAnonymously(
                organizationId,
                Map.of(
                        "name", "NoAuth Update " + suffix,
                        "meetingLanguage", "pt-BR",
                        "audioRetentionDays", 7));

        assertThat(updateResponse.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> post(Map<String, String> body, String bearer) {
        return client().post().uri("/api/organizations")
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }

    // The shared client() uses SimpleClientHttpRequestFactory (HttpURLConnection), which cannot send the
    // HTTP PATCH method. Use a JDK HttpClient-backed factory here, which supports PATCH.
    private RestClient patchClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    private ResponseEntity<String> patch(String organizationId, Map<String, Object> body, String bearer) {
        return patchClient().patch().uri("/api/organizations/{orgId}", organizationId)
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<String> patchAnonymously(String organizationId, Map<String, Object> body) {
        return patchClient().patch().uri("/api/organizations/{orgId}", organizationId)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
    }
}
