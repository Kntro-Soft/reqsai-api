package com.kntro.reqsai.gateway.application.port;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Application seam for the Atlassian OAuth 2.0 (3LO) endpoints (ADR-0023): authorization-code exchange,
 * refresh-token rotation, and accessible-resources discovery. The concrete HTTP lives in an
 * infrastructure adapter over {@code JiraOAuthClient}; application code programs against this port so it
 * never touches {@code infrastructure}. Tokens are opaque strings and are never logged by callers.
 */
public interface JiraOAuthPort {

    /** Exchanges an authorization {@code code} for the initial token set. */
    OAuthTokens exchangeCode(String code);

    /** Exchanges a {@code refreshToken} for a new (possibly rotated) token set. */
    OAuthTokens refresh(String refreshToken);

    /** Lists the Atlassian sites the {@code accessToken} can reach. */
    List<Site> accessibleResources(String accessToken);

    /**
     * A token set from Atlassian. {@code refreshToken} may be null on a refresh if the app does not rotate
     * refresh tokens; callers keep the prior refresh token in that case. {@code expiresInSeconds} is the
     * access-token lifetime.
     */
    record OAuthTokens(String accessToken, @Nullable String refreshToken, long expiresInSeconds, @Nullable String scope) {}

    /** An accessible Atlassian site: {@code cloudId} is used to build the OAuth Jira API base URL. */
    record Site(String cloudId, String url, String name) {}
}
