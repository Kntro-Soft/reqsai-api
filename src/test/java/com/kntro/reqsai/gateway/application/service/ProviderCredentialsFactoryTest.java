package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.IntegrationProvider.ProviderCredentials;
import com.kntro.reqsai.gateway.domain.model.CredentialType;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("Application: ProviderCredentialsFactory dual-mode routing")
@ExtendWith(MockitoExtension.class)
class ProviderCredentialsFactoryTest {

    @Mock
    private JiraOAuthTokenService oauthTokens;

    @Test
    @DisplayName("API_TOKEN connection yields basic-auth credentials from email + token")
    void api_token_routing() {
        IntegrationConnection connection = new IntegrationConnection(
                UUID.randomUUID(), IntegrationProviderType.JIRA,
                "https://acme.atlassian.net", "pm@acme.com", "tok", Instant.now());

        ProviderCredentials creds = new ProviderCredentialsFactory(oauthTokens).from(connection);

        assertThat(creds.credentialType()).isEqualTo(CredentialType.API_TOKEN);
        assertThat(creds.email()).isEqualTo("pm@acme.com");
        assertThat(creds.apiToken()).isEqualTo("tok");
        assertThat(creds.accessToken()).isNull();
    }

    @Test
    @DisplayName("OAUTH2 connection yields bearer credentials with a freshly resolved access token")
    void oauth_routing_uses_fresh_token() {
        IntegrationConnection connection = IntegrationConnection.oauth(
                UUID.randomUUID(), IntegrationProviderType.JIRA,
                "https://acme.atlassian.net", "cloud-1", "refresh", "access-stale",
                Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now());
        when(oauthTokens.freshAccessToken(connection)).thenReturn("access-fresh");

        ProviderCredentials creds = new ProviderCredentialsFactory(oauthTokens).from(connection);

        assertThat(creds.credentialType()).isEqualTo(CredentialType.OAUTH2);
        assertThat(creds.cloudId()).isEqualTo("cloud-1");
        assertThat(creds.accessToken()).isEqualTo("access-fresh");
        assertThat(creds.email()).isNull();
    }
}
