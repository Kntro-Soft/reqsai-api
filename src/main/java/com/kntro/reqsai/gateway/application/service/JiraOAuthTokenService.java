package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.OAuthTokens;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Ensures an OAuth 2.0 (3LO) {@link IntegrationConnection} has a usable, non-expired access token before a
 * provider call (ADR-0023). If the cached access token is missing, expired, or within {@link #SKEW} of
 * expiring, it refreshes via {@link JiraOAuthPort}, persists the rotated tokens (encrypted) + new expiry,
 * and returns the fresh access token. A refresh failure surfaces as {@code JIRA_AUTH_FAILED}. Tokens are
 * never logged.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JiraOAuthTokenService {

    /** Refresh a little before the token actually expires to avoid mid-call expiry races. */
    static final Duration SKEW = Duration.ofSeconds(60);

    private final JiraOAuthPort oauth;
    private final IntegrationConnectionRepository connections;

    /**
     * Returns a valid access token for {@code connection}, refreshing and persisting rotated tokens first
     * if the cached one is stale. Assumes {@code connection} is an OAUTH2 connection.
     */
    public String freshAccessToken(IntegrationConnection connection) {
        Instant now = Instant.now();
        if (!connection.oauthAccessExpiredWithin(SKEW, now)) {
            return connection.getOauthAccessToken();
        }
        String refreshToken = connection.getOauthRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw IntegrationsInfrastructureExceptions.jiraAuthFailed();
        }
        try {
            OAuthTokens tokens = oauth.refresh(refreshToken);
            Instant expiresAt = now.plusSeconds(tokens.expiresInSeconds());
            connection.applyRefreshedTokens(tokens.refreshToken(), tokens.accessToken(), expiresAt);
            connections.save(connection);
            return tokens.accessToken();
        } catch (InfrastructureException e) {
            log.warn("OAuth refresh failed for connection {} [{}]", connection.getId(), e.error().code());
            throw IntegrationsInfrastructureExceptions.jiraAuthFailed();
        }
    }
}
