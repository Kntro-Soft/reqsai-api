package com.kntro.reqsai.testsupport;

import io.jsonwebtoken.Jwts;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Mints RS256 access tokens signed with the throwaway test private key ({@code src/test/resources/certs}),
 * so integration / MVC tests can exercise the real {@code JwtAuthenticationFilter} and
 * {@code StompAuthChannelInterceptor} end-to-end. Claims mirror what {@code iam} will issue:
 * {@code sub}=userId, {@code orgId}, {@code role}; {@code iss}={@value #ISSUER} (matches application-test.yml).
 * <p>
 * For pure slice / method-security tests that don't need to cross the filter, prefer
 * {@link WithMockReqsaiUser} — it's faster and needs no token.
 */
public final class TestJwtFactory {

    /** Must match {@code reqsai.jwt.issuer} in application-test.yml. */
    public static final String ISSUER = "reqsai-test";

    private static final PrivateKey PRIVATE_KEY = loadPrivateKey();

    private TestJwtFactory() {
    }

    /** A signed, 15-minute access token for the given identity. */
    public static String token(String userId, String orgId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId)
                .claim("orgId", orgId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
                .signWith(PRIVATE_KEY, Jwts.SIG.RS256)
                .compact();
    }

    /** Convenience: the {@code Authorization} header value ({@code "Bearer <token>"}). */
    public static String bearer(String userId, String orgId, String role) {
        return "Bearer " + token(userId, orgId, role);
    }

    private static PrivateKey loadPrivateKey() {
        try (var is = new ClassPathResource("certs/private_key.pem").getInputStream()) {
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load test JWT private key", e);
        }
    }
}
