package com.kntro.reqsai.gateway.domain.exception;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

import java.util.UUID;

/**
 * Factory for Integrations domain exceptions — the context-specific counterpart of the shared
 * {@code Exceptions}. Not-found cases return {@link EntityNotFoundException}; the rest a
 * {@link DomainException} carrying an {@link IntegrationsError}.
 */
public final class IntegrationsExceptions {

    private IntegrationsExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static EntityNotFoundException connectionNotFound(UUID connectionId) {
        return new EntityNotFoundException(IntegrationsError.INTEGRATION_CONNECTION_NOT_FOUND,
                "Integration connection not found: " + connectionId);
    }

    /** A project has no Jira target configured — 404 for the GET target endpoint. */
    public static EntityNotFoundException targetNotFound(UUID projectId) {
        return new EntityNotFoundException(IntegrationsError.INTEGRATION_CONNECTION_NOT_FOUND,
                "No integration target configured for project " + projectId);
    }

    public static DomainException alreadyConnected(UUID organizationId, String provider) {
        return new DomainException(IntegrationsError.INTEGRATION_ALREADY_CONNECTED,
                "An active %s integration already exists for organization %s".formatted(provider, organizationId));
    }

    public static DomainException targetNotConfigured(UUID projectId) {
        return new DomainException(IntegrationsError.INTEGRATION_TARGET_NOT_CONFIGURED,
                "No integration target configured for project " + projectId);
    }

    public static EntityNotFoundException jiraProjectNotFound(String jiraProjectKey) {
        return new EntityNotFoundException(IntegrationsError.JIRA_PROJECT_NOT_FOUND,
                "Jira project not found: " + jiraProjectKey);
    }

    /** A story to push was not found in the project — 404 for the push endpoints. */
    public static EntityNotFoundException storyNotFound(UUID storyId) {
        return new EntityNotFoundException(IntegrationsError.INTEGRATION_CONNECTION_NOT_FOUND,
                "Story not found in project: " + storyId);
    }

    /** Jira OAuth is not configured on this deployment — the authorize/callback endpoints are unavailable. */
    public static DomainException oauthNotConfigured() {
        return new DomainException(IntegrationsError.JIRA_OAUTH_NOT_CONFIGURED,
                "Jira OAuth 2.0 (3LO) is not configured on this deployment");
    }

    /** The OAuth {@code state} token failed validation (signature/expiry/org-user mismatch). */
    public static DomainException oauthStateInvalid(String reason) {
        return new DomainException(IntegrationsError.JIRA_OAUTH_STATE_INVALID,
                "Invalid OAuth state: " + reason);
    }
}
