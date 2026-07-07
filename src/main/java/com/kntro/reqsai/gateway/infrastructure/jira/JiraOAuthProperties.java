package com.kntro.reqsai.gateway.infrastructure.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

/**
 * Jira OAuth 2.0 (3LO) app configuration bound from {@code reqsai.integrations.jira.oauth.*} (ADR-0022).
 * <p>
 * All fields are OPTIONAL: when {@link #clientId}, {@link #clientSecret} or {@link #redirectUri} is
 * blank the feature is considered <em>not configured</em> ({@link #configured()} is false) and the OAuth
 * endpoints answer {@code JIRA_OAUTH_NOT_CONFIGURED} — the app still boots (unlike the required
 * encryption key). Secrets are read from the environment and never logged.
 *
 * @param clientId     the OAuth app client id (blank ⇒ not configured)
 * @param clientSecret the OAuth app client secret (blank ⇒ not configured)
 * @param redirectUri  the registered callback URL (blank ⇒ not configured)
 * @param stateSecret  HMAC secret for signing the stateless {@code state} token; defaults to the
 *                     encryption key material when unset
 */
@ConfigurationProperties(prefix = "reqsai.integrations.jira.oauth")
public record JiraOAuthProperties(
        @Nullable String clientId,
        @Nullable String clientSecret,
        @Nullable String redirectUri,
        @Nullable String stateSecret
) {

    /** True only when the three app credentials required to run the flow are all present. */
    public boolean configured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri);
    }

    /** The HMAC signing secret, falling back to {@code client-secret} if no dedicated secret is set. */
    public String effectiveStateSecret() {
        return notBlank(stateSecret) ? stateSecret : (clientSecret == null ? "" : clientSecret);
    }

    private static boolean notBlank(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
