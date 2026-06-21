package com.kntro.reqsai.iam.infrastructure.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes for external-service failures in the IAM bounded context.
 * These codes represent infrastructure concerns and must NOT live in {@code IamError},
 * which is reserved for domain business-rule violations.
 *
 * @see com.kntro.reqsai.iam.domain.exception.IamError
 */
public enum IamInfrastructureError implements ErrorCatalog {

    EMAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    IamInfrastructureError(HttpStatus status) {
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
