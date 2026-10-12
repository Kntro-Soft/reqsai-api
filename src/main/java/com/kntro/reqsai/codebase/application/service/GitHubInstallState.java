package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Signs and verifies the stateless {@code state} of a GitHub App install, so the redirect back from GitHub
 * is tied to the organization and the user who started it, within a short window. Format:
 * {@code base64url(orgId|userId|expiry|nonce) + "." + base64url(HMAC-SHA256)}; the key derives from the
 * App's client secret, which only the server knows.
 */
@Component
public class GitHubInstallState {

    static final Duration TTL = Duration.ofMinutes(30);

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] key;

    public GitHubInstallState(@Value("${reqsai.codebase.github.app.client-secret:}") String clientSecret) {
        this.key = ("reqsai-github-install:" + clientSecret.strip()).getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID organizationId, UUID userId) {
        long expiry = Instant.now().plus(TTL).getEpochSecond();
        String payload = "%s|%s|%d|%s".formatted(organizationId, userId, expiry,
                UUID.randomUUID().toString().replace("-", ""));
        String encoded = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + B64.encodeToString(hmac(encoded));
    }

    /** Fails with {@code CODE_HOST_INSTALL_STATE_INVALID} unless the state was issued here for this org and user. */
    public void verify(String state, UUID organizationId, UUID userId) {
        if (state == null || state.isBlank()) throw CodebaseExceptions.installStateInvalid("missing state");
        int dot = state.indexOf('.');
        if (dot <= 0 || dot == state.length() - 1) throw CodebaseExceptions.installStateInvalid("malformed state");
        String encoded = state.substring(0, dot);
        byte[] provided;
        String payload;
        try {
            provided = B64D.decode(state.substring(dot + 1));
            payload = new String(B64D.decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw CodebaseExceptions.installStateInvalid("bad encoding");
        }
        if (!MessageDigest.isEqual(hmac(encoded), provided)) {
            throw CodebaseExceptions.installStateInvalid("signature mismatch");
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 4) throw CodebaseExceptions.installStateInvalid("malformed payload");
        if (!parts[0].equals(organizationId.toString()) || !parts[1].equals(userId.toString())) {
            throw CodebaseExceptions.installStateInvalid("issued for another organization or user");
        }
        try {
            if (Instant.now().getEpochSecond() > Long.parseLong(parts[2])) {
                throw CodebaseExceptions.installStateInvalid("expired");
            }
        } catch (NumberFormatException e) {
            throw CodebaseExceptions.installStateInvalid("bad expiry");
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Install state HMAC failed", e);
        }
    }
}
