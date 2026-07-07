package com.kntro.reqsai.gateway.application.port;

import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;

import java.util.List;

/**
 * Provider seam (ADR-0022): the capability of talking to a third-party tracker. Jira is the first
 * implementation ({@code JiraProvider}); adding another provider means adding an implementation keyed
 * by its {@link IntegrationProviderType}, with no change to the handlers or endpoints.
 *
 * <p>Credentials are passed explicitly (decrypted by the caller) so the provider never touches
 * persistence. Failures surface as infrastructure exceptions
 * ({@code JIRA_AUTH_FAILED} / {@code JIRA_UNREACHABLE} / {@code JIRA_PUSH_FAILED}).
 */
public interface IntegrationProvider {

    /** The provider this implementation serves. */
    IntegrationProviderType type();

    /** Verifies credentials, returning the authenticated account's display name. */
    String verify(ProviderCredentials credentials);

    /** Lists the projects visible to the credentials. */
    List<RemoteProject> listProjects(ProviderCredentials credentials);

    /** Lists issue types available for the given project key. */
    List<RemoteIssueType> listIssueTypes(ProviderCredentials credentials, String projectKey);

    /** Creates a tracker issue from a Reqs-AI story and returns its key + browse URL. */
    PushedIssue pushStory(ProviderCredentials credentials, String projectKey, String issueTypeName, StoryView story);

    /** Decrypted credentials for a single provider call (never persisted, never logged). */
    record ProviderCredentials(String siteUrl, String email, String apiToken) {}

    /** A remote project ({key,name}). */
    record RemoteProject(String key, String name) {}

    /** A remote issue type ({id,name}). */
    record RemoteIssueType(String id, String name) {}

    /** The result of a successful push ({issueKey, issueUrl}). */
    record PushedIssue(String issueKey, String issueUrl) {}
}
