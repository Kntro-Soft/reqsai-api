package com.kntro.reqsai.gateway.application.port;

import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.domain.model.CredentialType;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Provider seam (ADR-0023): the capability of talking to a third-party tracker. Jira is the first
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

    /**
     * Fetches the tracker issues eligible for import from {@code projectKey} of type {@code issueTypeName}
     * (all pages), each flattened to a provider-neutral {@link RemoteIssue} (summary + plain-text
     * description + mapped priority). The reverse of {@link #pushStory}.
     */
    List<RemoteIssue> searchImportableIssues(ProviderCredentials credentials, String projectKey, String issueTypeName);

    /**
     * Decrypted credentials for a single provider call (never persisted, never logged). Carries both
     * credential shapes; {@link #credentialType} selects which is populated:
     * <ul>
     *   <li>{@link CredentialType#API_TOKEN} — {@code siteUrl} + {@code email} + {@code apiToken}.</li>
     *   <li>{@link CredentialType#OAUTH2} — {@code siteUrl} (for browse URLs) + {@code cloudId} +
     *       {@code accessToken}. The access token is already fresh (refreshed by the caller if needed).</li>
     * </ul>
     */
    record ProviderCredentials(CredentialType credentialType, String siteUrl,
                               @Nullable String email, @Nullable String apiToken,
                               @Nullable String cloudId, @Nullable String accessToken) {

        /** API-token credentials (basic auth). */
        public static ProviderCredentials apiToken(String siteUrl, String email, String apiToken) {
            return new ProviderCredentials(CredentialType.API_TOKEN, siteUrl, email, apiToken, null, null);
        }

        /** OAuth 2.0 credentials (bearer auth); {@code accessToken} must already be valid. */
        public static ProviderCredentials oauth(String siteUrl, String cloudId, String accessToken) {
            return new ProviderCredentials(CredentialType.OAUTH2, siteUrl, null, null, cloudId, accessToken);
        }
    }

    /** A remote project ({key,name}). */
    record RemoteProject(String key, String name) {}

    /** A remote issue type ({id,name}). */
    record RemoteIssueType(String id, String name) {}

    /** The result of a successful push ({issueKey, issueUrl}). */
    record PushedIssue(String issueKey, String issueUrl) {}

    /**
     * A tracker issue eligible for import, flattened to provider-neutral fields. {@code priority} is a
     * Reqs-AI {@code Priority} name (the provider maps the tracker's priority scale); {@code description}
     * is plain text (ADF flattened for Jira). {@code issueType} is the tracker's type label.
     */
    record RemoteIssue(String issueKey, String summary, @Nullable String issueType,
                       @Nullable String description, String priority) {}
}
