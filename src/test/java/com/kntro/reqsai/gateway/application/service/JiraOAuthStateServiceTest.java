package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.config.JiraOAuthProperties;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsError;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Application: Jira OAuth state sign/verify")
class JiraOAuthStateServiceTest {

    private static final JiraOAuthProperties PROPS = new JiraOAuthProperties(
            "client-id", "client-secret", "https://cb", "state-secret-material-0123456789");

    private final JiraOAuthStateService service = new JiraOAuthStateService(PROPS);

    @Test
    @DisplayName("a freshly issued state verifies for its org+user")
    void round_trips() {
        UUID org = UUID.randomUUID();
        UUID user = UUID.randomUUID();

        String state = service.issue(org, user);

        assertThat(state).contains(".");
        service.verify(state, org, user); // does not throw
    }

    @Test
    @DisplayName("a tampered payload fails signature verification")
    void rejects_tampered() {
        UUID org = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String state = service.issue(org, user);
        // Flip the last character of the payload segment.
        int dot = state.indexOf('.');
        char[] chars = state.toCharArray();
        chars[dot - 1] = chars[dot - 1] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThatThrownBy(() -> service.verify(tampered, org, user))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(IntegrationsError.JIRA_OAUTH_STATE_INVALID);
    }

    @Test
    @DisplayName("a state issued for a different org is rejected")
    void rejects_wrong_org() {
        UUID user = UUID.randomUUID();
        String state = service.issue(UUID.randomUUID(), user);

        assertThatThrownBy(() -> service.verify(state, UUID.randomUUID(), user))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(IntegrationsError.JIRA_OAUTH_STATE_INVALID);
    }

    @Test
    @DisplayName("a state issued for a different user is rejected")
    void rejects_wrong_user() {
        UUID org = UUID.randomUUID();
        String state = service.issue(org, UUID.randomUUID());

        assertThatThrownBy(() -> service.verify(state, org, UUID.randomUUID()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("an expired state is rejected")
    void rejects_expired() {
        UUID org = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        // Build a state whose payload expiry is in the past but signed with the real secret, so only the
        // expiry check fails (not the signature). Reuses the service's own signing via reflection-free
        // reconstruction: issue then rewrite the expiry is not possible (would break the signature), so we
        // sign a hand-built expired payload the same way the service does.
        String expired = signExpired(org, user);

        assertThatThrownBy(() -> service.verify(expired, org, user))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(IntegrationsError.JIRA_OAUTH_STATE_INVALID);
    }

    @Test
    @DisplayName("a malformed state (no signature separator) is rejected")
    void rejects_malformed() {
        assertThatThrownBy(() -> service.verify("not-a-valid-state", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DomainException.class);
    }

    /** Signs an already-expired payload with the same HMAC the service uses, to exercise the expiry branch. */
    private static String signExpired(UUID org, UUID user) {
        try {
            String payload = "%s|%s|%d|%s".formatted(org, user, 1L, "noncevalue");
            var b64 = java.util.Base64.getUrlEncoder().withoutPadding();
            String encodedPayload = b64.encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    PROPS.effectiveStateSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(encodedPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return encodedPayload + "." + b64.encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
