package com.kntro.reqsai.discovery.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes owned by the Requirement Discovery bounded context. Mapped to RFC 9457 {@code ProblemDetail}
 * by the shared {@code GlobalExceptionHandler}. Only the codes used by the current slices are defined;
 * more are added as their use cases land.
 */
public enum DiscoveryError implements ErrorCatalog {

    DUPLICATE_USER_STORY(HttpStatus.CONFLICT),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_STORY_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    DiscoveryError(HttpStatus status) {
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
