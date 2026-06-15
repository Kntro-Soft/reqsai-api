package com.kntro.reqsai.discovery.interfaces.rest;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the create-discovery-session slice across the full multitenant flow: it first
 * creates an organization (which provisions its {@code tenant_<slug>} schema, including the
 * {@code discovery_sessions} table), then — with a JWT carrying that {@code orgId} — creates a session
 * and verifies the row landed in the tenant's schema. Proves the tenant routing end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Create Discovery Session")
class CreateDiscoverySessionIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create the session in the caller's tenant schema")
    void should_create_session_in_tenant_schema() {
        // Arrange — create an org (provisions its tenant schema + discovery_sessions table)
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        UUID projectId = UUID.randomUUID();

        // Act — create a session as a member of that org (token carries the orgId → routes to its schema)
        ResponseEntity<String> res = client().post().uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Sprint planning", "language", "es-PE"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert — HTTP
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).contains("\"status\":\"DRAFT\"");
        assertThat(res.getBody()).contains("\"projectId\":\"" + projectId + "\"");

        // Assert — row persisted in the tenant schema (not public)
        Integer inTenant = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"tenant_" + expectedSlug + "\".discovery_sessions WHERE project_id = ?::uuid",
                Integer.class, projectId.toString());
        assertThat(inTenant).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject an unauthenticated request")
    void should_reject_unauthenticated_request() {
        ResponseEntity<String> res = client().post().uri("/api/projects/{p}/sessions", UUID.randomUUID())
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "x", "language", "es-PE"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}
