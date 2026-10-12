package com.kntro.reqsai.overview.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes owned by the Overview bounded context, mapped to
 * {@code ProblemDetail} by the shared handler.
 */
public enum OverviewError implements ErrorCatalog {

    OVERVIEW_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND), OVERVIEW_ACCESS_DENIED(
            HttpStatus.FORBIDDEN), OVERVIEW_ORGANIZATION_MISMATCH(HttpStatus.FORBIDDEN);

    private final HttpStatus status;

    OverviewError(HttpStatus status) {
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
