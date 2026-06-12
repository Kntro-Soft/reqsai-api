package com.kntro.reqsai.shared.infrastructure.devtools;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Dev-only token minter — a stopgap until {@code iam} ships real login/refresh. It signs a genuine
 * RS256 JWT with the dev private key, so you can exercise authenticated endpoints (real authorization,
 * not a bypass) before the issuer exists. Active only under the {@code dev} profile; {@code /api/v1/auth/**}
 * is already public. {@code iam} replaces this with a real {@code TokenIssuer} + {@code /api/v1/auth/login}.
 * <p>
 * {@code GET /api/v1/auth/dev-token?userId=..&orgId=..&role=ROLE_USER} → returns a signed token to drop
 * into the {@code Authorization} header.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Profile("dev")
@Slf4j
public class DevTokenController {

    private final String privateKeyPath;
    private final String issuer;
    private PrivateKey privateKey;

    public DevTokenController(
            @Value("${reqsai.jwt.private-key-path:classpath:certs/private_key.pem}") String privateKeyPath,
            @Value("${reqsai.jwt.issuer:reqsai}") String issuer) {
        this.privateKeyPath = privateKeyPath;
        this.issuer = issuer;
    }

    @PostConstruct
    void init() throws Exception {
        var resource = new DefaultResourceLoader().getResource(privateKeyPath);
        try (InputStream is = resource.getInputStream()) {
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
        log.warn("DEV token endpoint active at GET /api/v1/auth/dev-token — dev profile only, never in prod");
    }

    @GetMapping("/dev-token")
    public Map<String, Object> devToken(
            @RequestParam(defaultValue = "00000000-0000-0000-0000-000000000001") String userId,
            @RequestParam(defaultValue = "00000000-0000-0000-0000-000000000009") String orgId,
            @RequestParam(defaultValue = "ROLE_USER") String role) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(userId)
                .claim("orgId", orgId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(8))))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return Map.of(
                "token", token,
                "authorizationHeader", "Bearer " + token,
                "userId", userId,
                "orgId", orgId,
                "role", role,
                "expiresInHours", 8);
    }
}
