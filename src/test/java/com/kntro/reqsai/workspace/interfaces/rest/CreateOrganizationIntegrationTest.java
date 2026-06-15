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

}
