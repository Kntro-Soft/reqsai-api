package com.kntro.reqsai.gateway.infrastructure.jira;

import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Jira Cloud implementation of {@link IntegrationProvider} (ADR-0022). Translates provider-neutral calls
 * into {@link JiraClient} REST calls and renders the story description as ADF via {@link JiraAdfBuilder}.
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
        return jira.verify(c.siteUrl(), c.email(), c.apiToken());
    }

    @Override
    public List<RemoteProject> listProjects(ProviderCredentials c) {
        return jira.listProjects(c.siteUrl(), c.email(), c.apiToken()).stream()
                .map(p -> new RemoteProject(p.key(), p.name()))
                .toList();
    }

    @Override
    public List<RemoteIssueType> listIssueTypes(ProviderCredentials c, String projectKey) {
        return jira.listIssueTypes(c.siteUrl(), c.email(), c.apiToken(), projectKey).stream()
                .map(t -> new RemoteIssueType(t.id(), t.name()))
                .toList();
    }

    @Override
    public PushedIssue pushStory(ProviderCredentials c, String projectKey, String issueTypeName, StoryView story) {
        Map<String, Object> description = JiraAdfBuilder.buildDescription(story);
        JiraClient.CreatedIssue created = jira.createIssue(
                c.siteUrl(), c.email(), c.apiToken(), projectKey, issueTypeName, story.title(), description);
        return new PushedIssue(created.key(), jira.browseUrl(c.siteUrl(), created.key()));
    }
}
