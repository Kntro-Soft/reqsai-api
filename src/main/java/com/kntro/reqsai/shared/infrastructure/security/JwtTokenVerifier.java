package com.kntro.reqsai.shared.infrastructure.security;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JJWT adapter for {@link TokenVerifier}: verifies RS256 access tokens with the RSA <strong>public</strong>
 * key (the private/signing key is not needed here — that belongs to {@code iam} issuance).
 * <p>
 * Claims: {@code sub} = user id, {@code orgId} = tenant id, {@code role}. The public key is loaded once
 * at startup from {@link JwtProperties#publicKeyPath()} (classpath or filesystem).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenVerifier implements TokenVerifier {

    public static final String CLAIM_ORG_ID = "orgId";
    public static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private PublicKey publicKey;

    @PostConstruct
    void init() {
        try {
            this.publicKey = loadPublicKey(properties.publicKeyPem(), properties.publicKeyPath());
            log.info("JWT verification public key loaded");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load JWT public key", e);
        }
    }

    @Override
    public VerifiedToken verify(String token) {
        Claims claims = parse(token);
        return new VerifiedToken(
                claims.getSubject(),
                claims.get(CLAIM_ORG_ID, String.class),
                claims.get(CLAIM_ROLE, String.class));
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw Exceptions.tokenExpired();
        } catch (JwtException | IllegalArgumentException e) {
            throw Exceptions.tokenInvalid();
        }
    }

    private PublicKey loadPublicKey(String pem, String location) throws Exception {
        if (pem != null && !pem.isBlank()) {
            return parsePublicKey(pem);
        }
        Resource resource = new DefaultResourceLoader().getResource(
                location.contains(":") ? location : "file:" + location);
        if (!resource.exists()) {
            resource = new DefaultResourceLoader().getResource("classpath:" + location);
        }
        if (!resource.exists()) {
            throw new IllegalStateException("JWT public key not found at: " + location);
        }
        try (InputStream is = resource.getInputStream()) {
            return parsePublicKey(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private PublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }
}
