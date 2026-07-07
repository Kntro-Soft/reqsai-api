package com.kntro.reqsai.gateway.infrastructure.jira;

import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.domain.model.CredentialType;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.JiraApiContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Jira Cloud implementation of {@link IntegrationProvider} (ADR-0022). Translates provider-neutral calls
 * into {@link JiraClient} REST calls and renders the story description as ADF via {@link JiraAdfBuilder}.
 * <p>
 * Dual-mode: {@link #contextFor(ProviderCredentials)} picks the base URL + {@code Authorization} header
 * from the credential type — basic auth against {@code https://{site}/rest/api/3} for API tokens, bearer
 * auth against {@code https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3} for OAuth. OAuth access
 * tokens arrive already fresh (refreshed upstream by {@code ProviderCredentialsFactory}).
 */
@Component
@RequiredArgsConstructor
public class JiraProvider implements IntegrationProvider {

    private final JiraClient jira;

    @Override
    public IntegrationProviderType type() {
        return IntegrationProviderType.JIRA;
    }

    @Override
    public String verify(ProviderCredentials c) {
        return jira.verify(contextFor(c));
    }

    @Override
    public List<RemoteProject> listProjects(ProviderCredentials c) {
        return jira.listProjects(contextFor(c)).stream()
                .map(p -> new RemoteProject(p.key(), p.name()))
                .toList();
    }

    @Override
    public List<RemoteIssueType> listIssueTypes(ProviderCredentials c, String projectKey) {
        return jira.listIssueTypes(contextFor(c), projectKey).stream()
                .map(t -> new RemoteIssueType(t.id(), t.name()))
                .toList();
    }

    @Override
    public PushedIssue pushStory(ProviderCredentials c, String projectKey, String issueTypeName, StoryView story) {
        JiraApiContext ctx = contextFor(c);
        Map<String, Object> description = JiraAdfBuilder.buildDescription(story);
        JiraClient.CreatedIssue created = jira.createIssue(ctx, projectKey, issueTypeName, story.title(), description);
        return new PushedIssue(created.key(), jira.browseUrl(ctx.browseBase(), created.key()));
    }

    /** Builds the base-URL + auth context for the credential's mode. */
    private static JiraApiContext contextFor(ProviderCredentials c) {
        if (c.credentialType() == CredentialType.OAUTH2) {
            return JiraApiContext.oauth(c.cloudId(), c.accessToken(), c.siteUrl());
        }
        return JiraApiContext.apiToken(c.siteUrl(), c.email(), c.apiToken());
    }
}
