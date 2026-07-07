package com.kntro.reqsai.gateway.infrastructure.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes for external-service and crypto failures in the Integrations bounded context (ADR-0022).
 * These are infrastructure concerns (Jira reachability/auth, encryption) and must NOT live in
 * {@link com.kntro.reqsai.gateway.domain.exception.IntegrationsError}.
 */
public enum IntegrationsInfrastructureError implements ErrorCatalog {

    JIRA_AUTH_FAILED(HttpStatus.UNAUTHORIZED),
    JIRA_UNREACHABLE(HttpStatus.BAD_GATEWAY),
    JIRA_PUSH_FAILED(HttpStatus.BAD_GATEWAY),
    JIRA_IMPORT_FAILED(HttpStatus.BAD_GATEWAY),
    INTEGRATION_ENCRYPTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    /** The Jira OAuth authorization-code / refresh-token exchange with Atlassian failed. */
    JIRA_OAUTH_EXCHANGE_FAILED(HttpStatus.BAD_GATEWAY);

    private final HttpStatus status;

    IntegrationsInfrastructureError(HttpStatus status) {
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
