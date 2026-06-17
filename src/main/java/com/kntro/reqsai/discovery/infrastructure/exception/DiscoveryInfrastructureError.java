package com.kntro.reqsai.discovery.infrastructure.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes for external-service failures in the Discovery bounded context.
 * These codes represent infrastructure concerns (service unavailability, API errors) and must NOT
 * live in {@code DiscoveryError}, which is reserved for domain business-rule violations.
 *
 * @see com.kntro.reqsai.discovery.domain.exception.DiscoveryError
 */
public enum DiscoveryInfrastructureError implements ErrorCatalog {

    REQUIREMENT_GENERATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    TRANSCRIPTION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    EMBEDDING_FAILED(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    DiscoveryInfrastructureError(HttpStatus status) {
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
