package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssueType;
import com.kntro.reqsai.gateway.application.query.ListJiraIssueTypesQuery;
import com.kntro.reqsai.gateway.application.service.ProviderCredentialsFactory;
import com.kntro.reqsai.gateway.application.service.ProviderRegistry;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lists the Jira issue types for a project key visible to a connection (live provider call). */
@Component
@RequiredArgsConstructor
public class ListJiraIssueTypesQueryHandler {

    private final IntegrationConnectionRepository connections;
    private final ProviderRegistry providers;
    private final ProviderCredentialsFactory credentials;

    @Transactional(readOnly = true)
    public List<RemoteIssueType> handle(ListJiraIssueTypesQuery query) {
        IntegrationConnection connection = connections
                .findByIdAndOrganizationId(query.connectionId(), query.organizationId())
                .orElseThrow(() -> IntegrationsExceptions.connectionNotFound(query.connectionId()));
        IntegrationProvider provider = providers.get(connection.getProvider());
        return provider.listIssueTypes(credentials.from(connection), query.projectKey());
    }
}
