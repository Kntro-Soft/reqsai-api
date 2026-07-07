package com.kntro.reqsai.gateway.infrastructure.jira;

import com.kntro.reqsai.gateway.application.port.JiraOAuthPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapts the {@link JiraOAuthPort} application port to the {@link JiraOAuthClient} HTTP client (ADR-0022),
 * translating the client's Jackson records into the port's value records. Keeps application code off
 * infrastructure.
 */
@Component
@RequiredArgsConstructor
public class JiraOAuthAdapter implements JiraOAuthPort {

    private final JiraOAuthClient client;

    @Override
    public OAuthTokens exchangeCode(String code) {
        return toTokens(client.exchangeCode(code));
    }

    @Override
    public OAuthTokens refresh(String refreshToken) {
        return toTokens(client.refresh(refreshToken));
    }

    @Override
    public List<Site> accessibleResources(String accessToken) {
        return client.accessibleResources(accessToken).stream()
                .map(r -> new Site(r.id(), r.url(), r.name()))
                .toList();
    }

    private static OAuthTokens toTokens(JiraOAuthClient.OAuthTokens t) {
        return new OAuthTokens(t.accessToken(), t.refreshToken(), t.expiresIn(), t.scope());
    }
}
