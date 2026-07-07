package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.application.config.JiraOAuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Signs and verifies the STATELESS OAuth {@code state} token (ADR-0022). The token binds the CSRF state
 * to the initiating {@code orgId} + {@code userId} with a short expiry and a random nonce, so nothing has
 * to be stored server-side and it survives the browser redirect. Format:
 * <pre>{@code base64url(orgId|userId|expiryEpochSeconds|nonce) + "." + base64url(HMAC-SHA256(payload))}</pre>
 * The HMAC key is the dedicated {@code reqsai.integrations.jira.oauth.state-secret} ({@code JIRA_OAUTH_STATE_SECRET}).
 * Verification checks the signature (constant-time), the expiry, and that the org/user match the caller;
 * any failure raises {@code JIRA_OAUTH_STATE_INVALID}.
 */
@Component
public class JiraOAuthStateService {

    /** How long an issued state token stays valid — long enough to complete consent, short enough to bound replay. */
    static final Duration TTL = Duration.ofMinutes(15);

    private static final String HMAC_ALG = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final JiraOAuthProperties props;
    private final SecureRandom random = new SecureRandom();

    public JiraOAuthStateService(JiraOAuthProperties props) {
        this.props = props;
    }

    /** Issues a signed state token for {@code orgId} + {@code userId}, valid for {@link #TTL}. */
    public String issue(UUID orgId, UUID userId) {
        long expiry = Instant.now().plus(TTL).getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String payload = "%s|%s|%d|%s".formatted(orgId, userId, expiry, nonce);
        String encodedPayload = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + B64.encodeToString(hmac(encodedPayload));
    }

    /**
     * Verifies {@code state} was issued for this {@code orgId} + {@code userId}, is unexpired, and has a
     * valid signature. Throws {@code JIRA_OAUTH_STATE_INVALID} on any failure.
     */
    public void verify(String state, UUID orgId, UUID userId) {
        if (state == null || state.isBlank()) {
            throw IntegrationsExceptions.oauthStateInvalid("missing state");
        }
        int dot = state.indexOf('.');
        if (dot <= 0 || dot == state.length() - 1) {
            throw IntegrationsExceptions.oauthStateInvalid("malformed state");
        }
        String encodedPayload = state.substring(0, dot);
        String signature = state.substring(dot + 1);

        byte[] expected = hmac(encodedPayload);
        byte[] provided;
        try {
            provided = B64D.decode(signature);
        } catch (IllegalArgumentException e) {
            throw IntegrationsExceptions.oauthStateInvalid("bad signature encoding");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw IntegrationsExceptions.oauthStateInvalid("signature mismatch");
        }

        String payload;
        try {
            payload = new String(B64D.decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw IntegrationsExceptions.oauthStateInvalid("bad payload encoding");
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            throw IntegrationsExceptions.oauthStateInvalid("malformed payload");
        }
        if (!parts[0].equals(orgId.toString()) || !parts[1].equals(userId.toString())) {
            throw IntegrationsExceptions.oauthStateInvalid("org/user mismatch");
        }
        long expiry;
        try {
            expiry = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw IntegrationsExceptions.oauthStateInvalid("bad expiry");
        }
        if (Instant.now().getEpochSecond() > expiry) {
            throw IntegrationsExceptions.oauthStateInvalid("expired");
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(props.effectiveStateSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // A misconfigured/empty secret is a server config problem, not a client one.
            throw new IllegalStateException("OAuth state HMAC failed", e);
        }
    }
}
