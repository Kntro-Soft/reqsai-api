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
@DisplayName("Integration: Add Project Constraint")
class AddProjectConstraintIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should add a constraint to a project")
    void should_add_constraint() {
        // Arrange — provision org + project
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Corp " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));

        String slug = "corp-" + suffix;
        UUID orgId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, slug));

        client().post().uri("/api/organizations/{orgId}/projects", orgId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Payment Platform", "programmingLanguages", List.of("Java"),
                        "frameworks", List.of("Spring Boot"), "clientPlatforms", List.of("Web"),
                        "databases", List.of("PostgreSQL"), "architecture", "Hexagonal", "domain", "Fintech"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));

        String schema = "tenant_" + slug;
        UUID projectId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id::text FROM \"" + schema + "\".projects WHERE name = 'Payment Platform'", String.class));

        // Act — add constraint
        ResponseEntity<String> res = client()
                .post().uri("/api/organizations/{orgId}/projects/{projectId}/constraints", orgId, projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("description", "Must comply with PCI-DSS."))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).contains("\"description\":\"Must comply with PCI-DSS.\"");

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"" + schema + "\".project_constraints WHERE description = 'Must comply with PCI-DSS.'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should return 404 when project does not exist")
    void should_return_404_when_project_not_found() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Solo " + suffix))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));

        UUID orgId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, "solo-" + suffix));

        ResponseEntity<String> res = client()
                .post().uri("/api/organizations/{orgId}/projects/{projectId}/constraints", orgId, UUID.randomUUID())
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("description", "PCI-DSS."))
                .exchange((req, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
