package com.kntro.reqsai.workspace.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes owned by the Workspace Management bounded context. Mapped to RFC 9457 {@code ProblemDetail}
 * by the shared {@code GlobalExceptionHandler}. Only the codes used by the current slice are defined;
 * more are added as their use cases land.
 */
public enum WorkspaceError implements ErrorCatalog {

    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    ORGANIZATION_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_GENERATION_SETTINGS(HttpStatus.BAD_REQUEST),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROJECT_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT),
    PROJECT_PLAN_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT);

    private final HttpStatus status;

    WorkspaceError(HttpStatus status) {
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
