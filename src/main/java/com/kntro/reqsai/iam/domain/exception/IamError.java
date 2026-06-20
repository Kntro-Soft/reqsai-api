package com.kntro.reqsai.iam.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes owned by the IAM bounded context. Mapped to RFC 9457 {@code ProblemDetail} by the shared
 * {@code GlobalExceptionHandler}. Only the codes used by the current slice are defined; more are added
 * as their use cases land.
 */
public enum IamError implements ErrorCatalog {

    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    INVALID_VERIFICATION_TOKEN(HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN),
    ACCOUNT_NOT_YET_VERIFIED(HttpStatus.FORBIDDEN),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ORGANIZATION_NOT_OWNED(HttpStatus.FORBIDDEN),
    CANNOT_SUSPEND_ACCOUNT(HttpStatus.CONFLICT),
    INVALID_PASSWORD_RESET_TOKEN(HttpStatus.UNAUTHORIZED);

    private final HttpStatus status;

    IamError(HttpStatus status) {
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
