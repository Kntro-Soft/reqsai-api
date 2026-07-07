package com.kntro.reqsai.integrations.application.handler;

import com.kntro.reqsai.integrations.application.command.ConnectJiraCommand;
import com.kntro.reqsai.integrations.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider;
import com.kntro.reqsai.integrations.application.service.ProviderRegistry;
import com.kntro.reqsai.integrations.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.integrations.domain.model.ConnectionStatus;
import com.kntro.reqsai.integrations.domain.model.IntegrationConnection;
import com.kntro.reqsai.integrations.domain.model.IntegrationProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Connects a Jira integration at the organization level: verifies the credential against Jira
 * (fail → {@code JIRA_AUTH_FAILED}/{@code JIRA_UNREACHABLE}) and, on success, persists an encrypted
 * connection. Rejects a second active connection with {@code INTEGRATION_ALREADY_CONNECTED}.
 */
@Component
@RequiredArgsConstructor
public class ConnectJiraCommandHandler {

    private final IntegrationConnectionRepository connections;
    private final ProviderRegistry providers;

    @Transactional
    public IntegrationConnection handle(ConnectJiraCommand command) {
        if (connections.existsByOrganizationIdAndProviderAndStatusNot(
                command.organizationId(), IntegrationProviderType.JIRA, ConnectionStatus.DISCONNECTED)) {
            throw IntegrationsExceptions.alreadyConnected(command.organizationId(), IntegrationProviderType.JIRA.name());
        }

        String siteUrl = IntegrationConnection.normalizeSiteUrl(command.siteUrl());
        IntegrationProvider provider = providers.get(IntegrationProviderType.JIRA);
        // Verify the credential BEFORE persisting anything. Throws on auth/reachability failure.
        provider.verify(new IntegrationProvider.ProviderCredentials(siteUrl, command.email(), command.apiToken()));

        IntegrationConnection connection = new IntegrationConnection(
                command.organizationId(), IntegrationProviderType.JIRA,
                siteUrl, command.email(), command.apiToken(), Instant.now());
        return connections.save(connection);
    }
}
