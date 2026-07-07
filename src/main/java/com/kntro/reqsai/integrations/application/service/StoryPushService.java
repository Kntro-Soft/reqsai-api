package com.kntro.reqsai.integrations.application.service;

import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.integrations.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider.ProviderCredentials;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider.PushedIssue;
import com.kntro.reqsai.integrations.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.integrations.domain.model.IntegrationConnection;
import com.kntro.reqsai.integrations.domain.model.ProjectIntegrationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared push mechanics used by both the single-story and push-all handlers: resolves the target's
 * connection + provider once, then pushes a story through the provider. Keeps the two handlers thin and
 * their credential/provider resolution identical.
 */
@Component
@RequiredArgsConstructor
public class StoryPushService {

    private final IntegrationConnectionRepository connections;
    private final ProviderRegistry providers;
    private final ProviderCredentialsFactory credentialsFactory;

    /** Resolves the connection + provider for a target, or throws if the connection has gone missing. */
    public PushContext contextFor(ProjectIntegrationTarget target) {
        IntegrationConnection connection = connections.findById(target.getConnectionId())
                .orElseThrow(() -> IntegrationsExceptions.connectionNotFound(target.getConnectionId()));
        IntegrationProvider provider = providers.get(connection.getProvider());
        return new PushContext(provider, credentialsFactory.from(connection),
                target.getJiraProjectKey(), target.getIssueTypeName());
    }

    /** Pushes one story within a resolved context. Throws an infrastructure exception on provider failure. */
    public PushedIssue push(PushContext ctx, StoryView story) {
        return ctx.provider().pushStory(ctx.credentials(), ctx.projectKey(), ctx.issueTypeName(), story);
    }

    /** Resolved-once push context for a project's target. */
    public record PushContext(IntegrationProvider provider, ProviderCredentials credentials,
                              String projectKey, String issueTypeName) {}
}
