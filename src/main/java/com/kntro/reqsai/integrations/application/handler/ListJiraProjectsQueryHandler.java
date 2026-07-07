package com.kntro.reqsai.integrations.application.handler;

import com.kntro.reqsai.integrations.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider.RemoteProject;
import com.kntro.reqsai.integrations.application.query.ListJiraProjectsQuery;
import com.kntro.reqsai.integrations.application.service.ProviderCredentialsFactory;
import com.kntro.reqsai.integrations.application.service.ProviderRegistry;
import com.kntro.reqsai.integrations.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.integrations.domain.model.IntegrationConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lists the Jira projects visible to a connection (live provider call). */
@Component
@RequiredArgsConstructor
public class ListJiraProjectsQueryHandler {

    private final IntegrationConnectionRepository connections;
    private final ProviderRegistry providers;
    private final ProviderCredentialsFactory credentials;

    @Transactional(readOnly = true)
    public List<RemoteProject> handle(ListJiraProjectsQuery query) {
        IntegrationConnection connection = connections
                .findByIdAndOrganizationId(query.connectionId(), query.organizationId())
                .orElseThrow(() -> IntegrationsExceptions.connectionNotFound(query.connectionId()));
        IntegrationProvider provider = providers.get(connection.getProvider());
        return provider.listProjects(credentials.from(connection));
    }
}
