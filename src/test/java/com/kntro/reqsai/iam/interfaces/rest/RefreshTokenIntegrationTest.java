package com.kntro.reqsai.iam.interfaces.rest;

import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubEmailConfig;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the IAM authentication endpoints covering HttpOnly cookie issuance, token rotation,
 * and sign-out cookie clearing. Uses a real Postgres via Testcontainers (inherited from
 * {@link AbstractIntegrationTest}) and the real Spring Security filter chain. Each test registers its own
 * unique account so there are no uniqueness collisions across parallel or repeated runs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@Import(StubEmailConfig.class)
@DisplayName("Integration: Refresh Token Cookie")
class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Registers a fresh account, activates it directly in DB (bypasses email flow), and returns email/password. */
    private String[] registerAccount() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = "iam-it-" + suffix + "@reqsai-test.local";
        String password = "T3stPass!" + suffix;

        ResponseEntity<String> resp = client().post()
                .uri("/api/auth/register")
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", password,
                        "firstName", "Test",
                        "lastName", "User"))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode())
                        .body(res.bodyTo(String.class)));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Activate the account directly — email verification is not exercised in this test suite
        jdbcTemplate.update("UPDATE public.accounts SET status = 'ACTIVE' WHERE email = ?", email);
        return new String[]{email, password};
    }

    /** Signs in and returns the raw {@code Set-Cookie} header value. */
    private ResponseEntity<String> signIn(String email, String password) {
        return client().post()
                .uri("/api/auth/login")
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode())
                        .headers(h -> h.addAll(res.getHeaders()))
                        .body(res.bodyTo(String.class)));
    }

    /** Extracts the raw cookie value from a {@code Set-Cookie} header, e.g. {@code rt=<value>; ...}. */
    private String extractCookieValue(List<String> setCookieHeaders) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith("rt="))
                .findFirst()
                .map(h -> h.split(";")[0].substring("rt=".length()))
                .orElseThrow(() -> new AssertionError("No 'rt' cookie found in Set-Cookie headers"));
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sign-in sets an HttpOnly refresh cookie and returns accessToken in body without refreshToken field")
    void signIn_setsRefreshCookie() {
        // Arrange
        String[] creds = registerAccount();

        // Act
        ResponseEntity<String> response = signIn(creds[0], creds[1]);

        // Assert — HTTP 200
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Assert — Set-Cookie carries the rt cookie with HttpOnly
        List<String> setCookie = response.getHeaders().get("Set-Cookie");
        assertThat(setCookie).isNotNull().isNotEmpty();
        String rtCookie = setCookie.stream().filter(h -> h.startsWith("rt=")).findFirst().orElse(null);
        assertThat(rtCookie).as("rt cookie must be present").isNotNull();
        assertThat(rtCookie).containsIgnoringCase("HttpOnly");

        // Assert — body has accessToken, no refreshToken field
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("\"accessToken\"");
        assertThat(body).doesNotContain("\"refreshToken\"");
    }

    @Test
    @DisplayName("refresh rotates the token and returns a new accessToken with a new cookie")
    void refresh_rotatesTokenAndReturnsNewAccessToken() {
        // Arrange — sign in to get the first cookie
        String[] creds = registerAccount();
        ResponseEntity<String> signInResp = signIn(creds[0], creds[1]);
        assertThat(signInResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> firstSetCookie = signInResp.getHeaders().get("Set-Cookie");
        String firstCookieValue = extractCookieValue(firstSetCookie);

        // Act — call /refresh with the cookie
        ResponseEntity<String> refreshResp = client().post()
                .uri("/api/auth/refresh")
                .header("Api-Version", "1")
                .header("Cookie", "rt=" + firstCookieValue)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode())
                        .headers(h -> h.addAll(res.getHeaders()))
                        .body(res.bodyTo(String.class)));

        // Assert — HTTP 200 with new access token
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResp.getBody()).contains("\"accessToken\"");

        // Assert — a new Set-Cookie was issued and the value is different (rotation)
        List<String> secondSetCookie = refreshResp.getHeaders().get("Set-Cookie");
        String secondCookieValue = extractCookieValue(secondSetCookie);
        assertThat(secondCookieValue).isNotEqualTo(firstCookieValue);
    }

    @Test
    @DisplayName("sign-out clears the refresh cookie with Max-Age=0 and returns 204")
    void signOut_clearsRefreshCookie() {
        // Arrange — sign in to get a valid cookie
        String[] creds = registerAccount();
        ResponseEntity<String> signInResp = signIn(creds[0], creds[1]);
        assertThat(signInResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> firstSetCookie = signInResp.getHeaders().get("Set-Cookie");
        String cookieValue = extractCookieValue(firstSetCookie);

        // Act — sign out
        ResponseEntity<String> signOutResp = client().post()
                .uri("/api/auth/logout")
                .header("Api-Version", "1")
                .header("Cookie", "rt=" + cookieValue)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode())
                        .headers(h -> h.addAll(res.getHeaders()))
                        .body(res.bodyTo(String.class)), false);

        // Assert — 204 No Content
        assertThat(signOutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Assert — Set-Cookie with Max-Age=0 to clear the cookie
        List<String> clearCookie = signOutResp.getHeaders().get("Set-Cookie");
        assertThat(clearCookie).isNotNull().isNotEmpty();
        String rtClearCookie = clearCookie.stream().filter(h -> h.startsWith("rt=")).findFirst().orElse(null);
        assertThat(rtClearCookie).as("rt cookie must be cleared").isNotNull();
        assertThat(rtClearCookie).containsIgnoringCase("Max-Age=0");
    }

    @Test
    @DisplayName("refresh returns 401 when the cookie contains an invalid token")
    void refresh_returns401WithInvalidToken() {
        // Act — call /refresh with a bogus cookie value
        ResponseEntity<String> response = client().post()
                .uri("/api/auth/refresh")
                .header("Api-Version", "1")
                .header("Cookie", "rt=bogus-token-value-that-does-not-exist-in-database")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode())
                        .body(res.bodyTo(String.class)), false);

        // Assert — 401 Unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
