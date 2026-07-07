package com.kntro.reqsai.integrations.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Domain (business-rule) error codes owned by the Integrations bounded context (ADR-0022). Mapped to
 * RFC 9457 {@code ProblemDetail} by the shared {@code GlobalExceptionHandler}. External-service
 * failures live in {@link com.kntro.reqsai.integrations.infrastructure.exception.IntegrationsInfrastructureError}.
 */
public enum IntegrationsError implements ErrorCatalog {

    INTEGRATION_CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTEGRATION_ALREADY_CONNECTED(HttpStatus.CONFLICT),
    INTEGRATION_TARGET_NOT_CONFIGURED(HttpStatus.CONFLICT),
    JIRA_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND);

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
