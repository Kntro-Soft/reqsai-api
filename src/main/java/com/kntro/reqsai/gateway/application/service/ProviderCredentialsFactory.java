package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.IntegrationProvider.ProviderCredentials;
import com.kntro.reqsai.gateway.domain.model.CredentialType;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds provider {@link ProviderCredentials} from a persisted {@link IntegrationConnection}, decrypting
 * the secret (the getters return decrypted values via the JPA converter). Isolated so the decryption
 * point is single and obvious; the result is short-lived and never logged.
 * <p>
 * For {@link CredentialType#OAUTH2} connections it first ensures a fresh access token via
 * {@link JiraOAuthTokenService} (refreshing + persisting rotated tokens if the cached one is stale), so
 * the provider always receives a usable bearer token.
 */
@Component
@RequiredArgsConstructor
public class ProviderCredentialsFactory {

    private final JiraOAuthTokenService oauthTokens;

    public ProviderCredentials from(IntegrationConnection connection) {
        if (connection.getCredentialType() == CredentialType.OAUTH2) {
            String accessToken = oauthTokens.freshAccessToken(connection);
            return ProviderCredentials.oauth(connection.getSiteUrl(), connection.getCloudId(), accessToken);
        }
        return ProviderCredentials.apiToken(
                connection.getSiteUrl(), connection.getEmail(), connection.getApiToken());
    }
}
