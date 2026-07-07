package com.kntro.reqsai.gateway.infrastructure.exception;

import com.kntro.reqsai.shared.domain.exception.InfrastructureException;

/**
 * Factory for Integrations infrastructure exceptions — the infrastructure counterpart of
 * {@link com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions}. Adapters use this
 * factory instead of constructing {@link InfrastructureException} inline. Messages never include the
 * Jira token.
 */
public final class IntegrationsInfrastructureExceptions {

    private IntegrationsInfrastructureExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static InfrastructureException jiraAuthFailed() {
        return new InfrastructureException(IntegrationsInfrastructureError.JIRA_AUTH_FAILED,
                "Jira rejected the credentials (401/403)", null);
    }

    public static InfrastructureException jiraUnreachable(String reason, Throwable cause) {
        return new InfrastructureException(IntegrationsInfrastructureError.JIRA_UNREACHABLE,
                "Jira is unreachable: " + reason, cause);
    }

    public static InfrastructureException jiraPushFailed(String reason) {
        return new InfrastructureException(IntegrationsInfrastructureError.JIRA_PUSH_FAILED,
                "Jira rejected the issue creation: " + reason, null);
    }

    public static InfrastructureException encryptionError(String reason, Throwable cause) {
        return new InfrastructureException(IntegrationsInfrastructureError.INTEGRATION_ENCRYPTION_ERROR,
                "Integration secret encryption failed: " + reason, cause);
    }

    /** The Jira OAuth token/refresh exchange with Atlassian failed. Never includes any token. */
    public static InfrastructureException jiraOauthExchangeFailed(String reason, Throwable cause) {
        return new InfrastructureException(IntegrationsInfrastructureError.JIRA_OAUTH_EXCHANGE_FAILED,
                "Jira OAuth token exchange failed: " + reason, cause);
    }
}
