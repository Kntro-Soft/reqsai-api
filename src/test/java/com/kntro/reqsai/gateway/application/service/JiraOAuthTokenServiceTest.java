package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.OAuthTokens;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Jira OAuth token refresh-before-call")
@ExtendWith(MockitoExtension.class)
class JiraOAuthTokenServiceTest {

    @Mock
    private JiraOAuthPort oauth;
    @Mock
    private IntegrationConnectionRepository connections;

    private JiraOAuthTokenService service() {
        return new JiraOAuthTokenService(oauth, connections);
    }

    private IntegrationConnection oauthConnection(Instant accessExpiry) {
        return IntegrationConnection.oauth(
                UUID.randomUUID(), IntegrationProviderType.JIRA,
                "https://acme.atlassian.net", "cloud-1",
                "refresh-old", "access-old", accessExpiry, Instant.now());
    }

    @Test
    @DisplayName("returns the cached access token when it is still valid")
    void uses_cached_when_valid() {
        IntegrationConnection connection = oauthConnection(Instant.now().plus(30, ChronoUnit.MINUTES));

        String token = service().freshAccessToken(connection);

        assertThat(token).isEqualTo("access-old");
        verify(oauth, never()).refresh(any());
        verify(connections, never()).save(any());
    }

    @Test
    @DisplayName("refreshes and persists rotated tokens when the access token has expired")
    void refreshes_when_expired() {
        IntegrationConnection connection = oauthConnection(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(oauth.refresh("refresh-old"))
                .thenReturn(new OAuthTokens("access-new", "refresh-rotated", 3600, "scope"));
        when(connections.save(any(IntegrationConnection.class))).thenAnswer(i -> i.getArgument(0));

        String token = service().freshAccessToken(connection);

        assertThat(token).isEqualTo("access-new");
        // Rotated refresh token is persisted; the cached access token + expiry are updated.
        assertThat(connection.getOauthRefreshToken()).isEqualTo("refresh-rotated");
        assertThat(connection.getOauthAccessToken()).isEqualTo("access-new");
        assertThat(connection.getOauthAccessExpiresAt()).isAfter(Instant.now());
        verify(connections).save(connection);
    }

    @Test
    @DisplayName("keeps the existing refresh token when the refresh response omits a new one")
    void keeps_refresh_when_not_rotated() {
        IntegrationConnection connection = oauthConnection(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(oauth.refresh("refresh-old"))
                .thenReturn(new OAuthTokens("access-new", null, 3600, "scope"));
        when(connections.save(any(IntegrationConnection.class))).thenAnswer(i -> i.getArgument(0));

        service().freshAccessToken(connection);

        assertThat(connection.getOauthRefreshToken()).isEqualTo("refresh-old");
        assertThat(connection.getOauthAccessToken()).isEqualTo("access-new");
    }

    @Test
    @DisplayName("a refresh failure surfaces as JIRA_AUTH_FAILED")
    void refresh_failure_maps_to_auth_failed() {
        IntegrationConnection connection = oauthConnection(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(oauth.refresh("refresh-old"))
                .thenThrow(com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions
                        .jiraOauthExchangeFailed("boom", null));

        assertThatThrownBy(() -> service().freshAccessToken(connection))
                .isInstanceOf(InfrastructureException.class)
                .extracting(e -> ((InfrastructureException) e).error().code())
                .isEqualTo("JIRA_AUTH_FAILED");
    }
}
