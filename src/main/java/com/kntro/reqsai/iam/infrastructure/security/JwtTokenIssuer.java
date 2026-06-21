package com.kntro.reqsai.iam.infrastructure.security;

import com.kntro.reqsai.iam.application.port.IssuedToken;
import com.kntro.reqsai.iam.application.port.TokenIssuer;
import com.kntro.reqsai.shared.infrastructure.security.JwtTokenVerifier;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JJWT adapter for {@link TokenIssuer}: mints RS256 access tokens signed with the RSA <strong>private</strong>
 * key. This is the real issuer the {@code DevTokenController} stopgap pointed to; the Shared Kernel's
 * {@link JwtTokenVerifier} verifies these tokens with the matching public key. Claims mirror the verifier:
 * {@code sub}=userId, {@code orgId} (optional), {@code role}. The signing key is loaded once at startup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenIssuer implements TokenIssuer {

    private final IamJwtProperties properties;
    private PrivateKey privateKey;

    @PostConstruct
    void init() {
        try {
            this.privateKey = loadPrivateKey(properties.privateKeyPath());
            log.info("JWT signing private key loaded — IAM token issuer ready");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load JWT private key", e);
        }
    }

    static final String CLAIM_TERMS_VERSION = "termsVersion";

    @Override
    public IssuedToken issue(UUID userId, @Nullable UUID orgId, String role, @Nullable String termsVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenExpiration());

        var builder = Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .claim(JwtTokenVerifier.CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256);

        if (orgId != null) {
            builder.claim(JwtTokenVerifier.CLAIM_ORG_ID, orgId.toString());
        }
        if (termsVersion != null) {
            builder.claim(CLAIM_TERMS_VERSION, termsVersion);
        }

        return new IssuedToken(builder.compact(), properties.accessTokenExpiration().toSeconds());
    }

    private PrivateKey loadPrivateKey(String location) throws Exception {
        Resource resource = new DefaultResourceLoader().getResource(
                location.contains(":") ? location : "file:" + location);
        if (!resource.exists()) {
            resource = new DefaultResourceLoader().getResource("classpath:" + location);
        }
        if (!resource.exists()) {
            throw new IllegalStateException("JWT private key not found at: " + location);
        }
        try (InputStream is = resource.getInputStream()) {
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
    }
}
