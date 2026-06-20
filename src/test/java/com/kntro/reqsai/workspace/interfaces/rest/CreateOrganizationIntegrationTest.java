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

/**
 * Integration test of the create-organization vertical slice: a real JWT crosses the security chain into
 * {@code OrganizationController} → handler → repository ({@code public.organizations}) →
 * {@code ProvisioningService} (creates the {@code tenant_<slug>} schema). Backed by a real Postgres via
 * the Testcontainers JDBC driver. Each run uses a <strong>random</strong> org name/slug, so it is
 * repeatable and isolated (no collisions on the unique slug or the provisioned schema).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Create Organization")
class CreateOrganizationIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ORG_ID = "00000000-0000-0000-0000-000000000009";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should persist the org and provision its tenant schema")
    void should_persist_org_and_provision_tenant_schema() {
        // Arrange — random, collision-free identity for this run
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String name = "Acme " + suffix;
        String expectedSlug = "acme-" + suffix;

        // Act
        ResponseEntity<String> response = post(Map.of("name", name, "meetingLanguage", "en-US"),
                TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));

        // Assert — HTTP
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"slug\":\"" + expectedSlug + "\"");
        assertThat(response.getBody()).contains("\"status\":\"ACTIVE\"");
        assertThat(response.getBody()).contains("\"ownerId\":\"" + USER_ID + "\"");

        // Assert — registry row landed in public.organizations as ACTIVE
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        assertThat(status).isEqualTo("ACTIVE");

        // Assert — tenant schema was provisioned
        Integer schemas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, "tenant_" + expectedSlug);
        assertThat(schemas).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject an unauthenticated request")
    void should_reject_unauthenticated_request() {
        // Act
        ResponseEntity<String> response = postAnonymously(Map.of("name", "NoAuth Inc"));

        // Assert
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

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

        ResponseEntity<String> updateResponse = put(
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

        ResponseEntity<String> updateResponse = put(
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

        ResponseEntity<String> updateResponse = putAnonymously(
                organizationId,
                Map.of(
                        "name", "NoAuth Update " + suffix,
                        "meetingLanguage", "pt-BR",
                        "audioRetentionDays", 7));

        assertThat(updateResponse.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // HTTP helpers

    private ResponseEntity<String> post(Map<String, String> body, String bearer) {
        return client().post().uri("/api/organizations")
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }

    private ResponseEntity<String> postAnonymously(Map<String, String> body) {
        return client().post().uri("/api/organizations")
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
    }

    private ResponseEntity<String> put(String organizationId, Map<String, Object> body, String bearer) {
        return client().put().uri("/api/organizations/{orgId}", organizationId)
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }

    private ResponseEntity<String> putAnonymously(String organizationId, Map<String, Object> body) {
        return client().put().uri("/api/organizations/{orgId}", organizationId)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
    }

}
