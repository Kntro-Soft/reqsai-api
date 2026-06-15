package com.kntro.reqsai;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test of the running foundation — the automated version of the manual {@code bootRun}
 * checks. Uses {@code RANDOM_PORT} so it never collides with another local process on 8080/8090.
 * <p>
 * Each assertion pins one thing the manual smoke test verified: health is UP, protected routes reject
 * anonymous calls, the correlation filter emits {@code X-Request-ID}, and the served OpenAPI is ours.
 * <p>
 * Uses {@link RestClient} (from spring-web, already on the classpath) rather than {@code TestRestTemplate}
 * to avoid Boot 4's split test-client modules. {@code exchange(...)} returns the raw response so 4xx/5xx
 * do not throw.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class SmokeTest extends AbstractIntegrationTest {

    /** GET that captures status + headers + body without throwing on non-2xx. */
    private ResponseEntity<String> get(String path) {
        return client().get().uri(path).exchange((request, response) ->
                ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.bodyTo(String.class)));
    }

    @Test
    void healthIsUp() {
        ResponseEntity<String> res = get("/actuator/health");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessProbeIsUp() {
        ResponseEntity<String> res = get("/actuator/health/readiness");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void protectedRouteRejectsAnonymousRequest() {
        // Any non-public path must be denied without a token (foundation security wiring).
        HttpStatusCode status = get("/api/v1/workspaces").getStatusCode();
        assertThat(status).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void correlationIdHeaderIsPresent() {
        ResponseEntity<String> res = get("/actuator/health");
        assertThat(res.getHeaders().headerSet())
                .anyMatch(e -> e.getKey().equalsIgnoreCase("X-Request-ID"));
    }

    @Test
    void servesOwnOpenApiDocument() {
        ResponseEntity<String> res = get("/api-docs");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("Reqs-AI");
    }

    @Test
    void protectedActuatorEndpointRequiresAuth() {
        // metrics is exposed but must be secured (only health/** is public).
        HttpStatusCode status = get("/actuator/metrics").getStatusCode();
        assertThat(status).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void validJwtClearsTheSecurityChain() {
        ResponseEntity<String> res = client().get().uri("/api/v1/whoami")
                .header("Authorization", TestJwtFactory.bearer(
                        "00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000009",
                        "ROLE_USER"))
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
        assertThat(res.getStatusCode())
                .isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
