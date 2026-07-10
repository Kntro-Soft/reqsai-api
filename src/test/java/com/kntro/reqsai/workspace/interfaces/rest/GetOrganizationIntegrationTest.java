package com.kntro.reqsai.workspace.interfaces.rest;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Get Organization")
class GetOrganizationIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000003";
    private static final String ORG_ID = "00000000-0000-0000-0000-000000000009";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should return the organization for its owner")
    void should_return_the_organization_for_its_owner() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", "Acme " + suffix, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = extractOrganizationId(expectedSlug);

        ResponseEntity<String> getResponse = get(
                organizationId,
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).contains("\"slug\":\"" + expectedSlug + "\"");
        assertThat(getResponse.getBody()).contains("\"ownerId\":\"" + USER_ID + "\"");
        assertThat(getResponse.getBody()).contains("\"meetingLanguage\":\"en-US\"");
        assertThat(getResponse.getBody()).contains("\"audioRetentionDays\":30");
    }

    @Test
    @DisplayName("should return the organization for an org admin")
    void should_return_the_organization_for_an_admin() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", "Acme " + suffix, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = extractOrganizationId(expectedSlug);
        createMember(organizationId, USER_ID, Map.of(
                "userId", ADMIN_USER_ID, "email", "admin@example.com", "displayName", "Admin", "role", "ADMIN"));

        ResponseEntity<String> getResponse = get(
                organizationId,
                TestJwtFactory.bearer(ADMIN_USER_ID, organizationId, "ROLE_USER"));

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).contains("\"slug\":\"" + expectedSlug + "\"");
        assertThat(getResponse.getBody()).contains("\"ownerId\":\"" + USER_ID + "\"");
    }

    @Test
    @DisplayName("should reject get from a non-member")
    void should_reject_get_from_non_owner() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", "Acme " + suffix, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = extractOrganizationId(expectedSlug);

        ResponseEntity<String> getResponse = get(
                organizationId,
                TestJwtFactory.bearer("00000000-0000-0000-0000-000000000002", ORG_ID, "ROLE_USER"));

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should reject unauthenticated get request")
    void should_reject_unauthenticated_get_request() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;

        ResponseEntity<String> createResponse = post(
                Map.of("name", "Acme " + suffix, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String organizationId = extractOrganizationId(expectedSlug);

        ResponseEntity<String> getResponse = getAnonymously(organizationId);

        assertThat(getResponse.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private String extractOrganizationId(String expectedSlug) {
        return jdbcTemplate.queryForObject(
                "SELECT id::text FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
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

    private ResponseEntity<String> get(String organizationId, String bearer) {
        return client().get().uri("/api/organizations/{orgId}", organizationId)
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }

    private ResponseEntity<String> getAnonymously(String organizationId) {
        return client().get().uri("/api/organizations/{orgId}", organizationId)
                .header("Api-Version", "1")
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
    }

    private void createMember(String organizationId, String ownerUserId, Map<String, Object> body) {
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/members", organizationId)
                .header("Authorization", TestJwtFactory.bearer(ownerUserId, organizationId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
