package com.kntro.reqsai.gateway.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Domain (business-rule) error codes owned by the Integrations bounded context (ADR-0023). Mapped to
 * RFC 9457 {@code ProblemDetail} by the shared {@code GlobalExceptionHandler}. External-service
 * failures live in {@link com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureError}.
 */
public enum IntegrationsError implements ErrorCatalog {

    INTEGRATION_CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTEGRATION_ALREADY_CONNECTED(HttpStatus.CONFLICT),
    INTEGRATION_TARGET_NOT_CONFIGURED(HttpStatus.CONFLICT),

    /** A background sync job (import / push-all) of the same type is already RUNNING for the project. */
    INTEGRATION_JOB_ALREADY_RUNNING(HttpStatus.CONFLICT),

    /** No sync job with the requested id exists for the project. */
    INTEGRATION_JOB_NOT_FOUND(HttpStatus.NOT_FOUND),
    JIRA_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Jira OAuth 2.0 (3LO) is not configured on this deployment (client id/secret/redirect absent). */
    JIRA_OAUTH_NOT_CONFIGURED(HttpStatus.NOT_IMPLEMENTED),

    /** The OAuth {@code state} token failed validation (bad signature, expired, or wrong org/user). */
    JIRA_OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    IntegrationsError(HttpStatus status) {
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
