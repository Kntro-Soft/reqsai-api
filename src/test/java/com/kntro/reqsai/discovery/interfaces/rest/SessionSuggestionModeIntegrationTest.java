package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US46 over HTTP: the analyst switches a session to on-demand analysis and asks for an analysis with
 * "Analizar ahora", which only runs while the meeting is being captured.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Session suggestion mode and on-demand analysis (US46)")
class SessionSuggestionModeIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_USER_ID = "00000000-0000-0000-0000-00000000000a";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port:0}")
    private int serverPort;

    @Test
    @DisplayName("switches to MANUAL, refuses to analyze a DRAFT, analyzes a live session on demand, and needs SESSION_RUN")
    void suggestion_mode_and_analyze_now() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "acme-" + suffix;
        UUID orgId = createOrganization(suffix, slug);
        UUID projectId = createProject(orgId, slug);
        assertThat(post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/members", Map.of(
                "userId", MEMBER_USER_ID, "email", "member@example.com", "displayName", "Member", "role", "MEMBER"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> created = post(OWNER_USER_ID, orgId, "/api/projects/" + projectId + "/sessions",
                Map.of("title", "Kickoff", "language", "es-PE"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"suggestionMode\":\"AUTO\"");
        String sessionId = created.getBody().split("\"id\":\"")[1].split("\"")[0];
        String base = "/api/projects/" + projectId + "/sessions/" + sessionId;

        ResponseEntity<String> manual = patch(OWNER_USER_ID, orgId, base + "/suggestion-mode", Map.of("mode", "MANUAL"));
        assertThat(manual.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(manual.getBody()).contains("\"suggestionMode\":\"MANUAL\"");

        // Only while the meeting is being captured.
        ResponseEntity<String> tooEarly = post(OWNER_USER_ID, orgId, base + "/analyze", null);
        assertThat(tooEarly.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(tooEarly.getBody()).contains("INVALID_SESSION_STATUS");

        assertThat(post(OWNER_USER_ID, orgId, base + "/start", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> analyzed = post(OWNER_USER_ID, orgId, base + "/analyze", null);
        assertThat(analyzed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(analyzed.getBody()).contains("\"suggestionsCreated\":0");

        // A member with the READ floor can neither switch the mode nor ask for an analysis.
        assertThat(patch(MEMBER_USER_ID, orgId, base + "/suggestion-mode", Map.of("mode", "AUTO")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(MEMBER_USER_ID, orgId, base + "/analyze", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- helpers -----

    private ResponseEntity<String> post(String userId, UUID orgId, String uri, Map<String, Object> body) {
        var spec = client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1");
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    /** JDK HttpClient supports PATCH (SimpleClientHttpRequestFactory does not). */
    private ResponseEntity<String> patch(String userId, UUID orgId, String uri, Map<String, Object> body) {
        RestClient patchClient = RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        return patchClient.patch().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(userId, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
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

    private UUID createProject(UUID orgId, String slug) {
        ResponseEntity<String> res = post(OWNER_USER_ID, orgId, "/api/organizations/" + orgId + "/projects", Map.of(
                "name", "Mode Project",
                "programmingLanguages", List.of("Java"),
                "frameworks", List.of("Spring Boot"),
                "clientPlatforms", List.of("Web"),
                "databases", List.of("PostgreSQL"),
                "architecture", "Hexagonal",
                "domain", "Gastronomía"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"tenant_" + slug + "\".projects WHERE organization_id = ?::uuid AND name = ?",
                String.class, orgId.toString(), "Mode Project"));
    }
}
