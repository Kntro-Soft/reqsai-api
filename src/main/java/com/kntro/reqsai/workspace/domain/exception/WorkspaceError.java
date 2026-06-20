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
    ORGANIZATION_EDIT_PERMISSION_DENIED(HttpStatus.FORBIDDEN),
    INVALID_GENERATION_SETTINGS(HttpStatus.BAD_REQUEST),
    GLOSSARY_NOT_FOUND(HttpStatus.NOT_FOUND),
    GLOSSARY_TERM_NOT_FOUND(HttpStatus.NOT_FOUND),
    GLOSSARY_TERM_ALREADY_EXISTS(HttpStatus.CONFLICT),
    GLOSSARY_TERM_PLAN_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROJECT_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT),
    PROJECT_PLAN_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
    PROJECT_CONSTRAINT_ALREADY_EXISTS(HttpStatus.CONFLICT),
    PROJECT_CONSTRAINT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROJECT_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROJECT_DOCUMENT_ALREADY_EXISTS(HttpStatus.CONFLICT),
    PROJECT_DOCUMENT_PLAN_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT);

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
