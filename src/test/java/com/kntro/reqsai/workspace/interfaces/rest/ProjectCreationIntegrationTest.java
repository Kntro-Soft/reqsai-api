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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Create Project")
class ProjectCreationIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should create a project and provision its glossary in the tenant schema")
    void should_create_project_and_provision_glossary() {
        // Arrange - create organization to provision schema
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expectedSlug = "acme-" + suffix;
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orgIdStr = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, expectedSlug);
        UUID orgId = UUID.fromString(orgIdStr);

        Map<String, Object> request = Map.of(
                "name", "My First Project",
                "description", "A cool project",
                "programmingLanguages", List.of("Java", "TypeScript"),
                "frameworks", List.of("Spring Boot", "Next.js"),
                "clientPlatforms", List.of("Web", "Mobile"),
                "databases", List.of("PostgreSQL", "Redis"),
                "architecture", "Clean Architecture",
                "domain", "Fintech"
        );

        // Act - create project
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert - HTTP
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).contains("\"name\":\"My First Project\"");
        assertThat(res.getBody()).contains("\"status\":\"ACTIVE\"");

        // Find created project ID from response or DB
        String schema = "tenant_" + expectedSlug;
        Map<String, Object> projectRow = jdbcTemplate.queryForMap(
                "SELECT id::text as id, name FROM \"" + schema + "\".projects WHERE organization_id = ?::uuid",
                orgId
        );
        assertThat(projectRow.get("name")).isEqualTo("My First Project");
        UUID projectId = UUID.fromString((String) projectRow.get("id"));

        // Assert - glossary auto-created (1:1 with project) in the tenant schema
        Integer glossaryCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM \"" + schema + "\".glossaries WHERE project_id = ?::uuid",
                Integer.class, projectId
        );
        assertThat(glossaryCount).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject unauthenticated request")
    void should_reject_unauthenticated() {
        ResponseEntity<String> res = client().post().uri("/api/organizations/{orgId}/projects", UUID.randomUUID())
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Project No Auth"))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)), false);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
