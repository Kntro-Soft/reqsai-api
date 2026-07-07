package com.kntro.reqsai.integrations.application.service;

import com.kntro.reqsai.integrations.application.port.IntegrationProvider.ProviderCredentials;
import com.kntro.reqsai.integrations.domain.model.IntegrationConnection;
import org.springframework.stereotype.Component;

/**
 * Builds provider {@link ProviderCredentials} from a persisted {@link IntegrationConnection}, decrypting
 * the token (the {@code apiToken} getter returns the decrypted value via the JPA converter). Isolated so
 * the decryption point is single and obvious; the result is short-lived and never logged.
 */
@Component
public class ProviderCredentialsFactory {

    public ProviderCredentials from(IntegrationConnection connection) {
        return new ProviderCredentials(connection.getSiteUrl(), connection.getEmail(), connection.getApiToken());
    }
}
