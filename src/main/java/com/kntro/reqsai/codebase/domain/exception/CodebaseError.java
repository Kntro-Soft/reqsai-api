package com.kntro.reqsai.codebase.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/** Error codes owned by the Codebase bounded context, mapped to {@code ProblemDetail} by the shared handler. */
public enum CodebaseError implements ErrorCatalog {

    CODE_REPOSITORY_URL_INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
    CODE_REPOSITORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    CODE_REPOSITORY_ACCESS_DENIED(HttpStatus.UNPROCESSABLE_CONTENT),
    CODE_REPOSITORY_ALREADY_CONNECTED(HttpStatus.CONFLICT),
    CODE_REPOSITORY_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT),
    CODE_REPOSITORY_INDEXING(HttpStatus.CONFLICT),
    CODE_REPOSITORY_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT),
    CODE_HOST_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    CODE_HOST_APP_NOT_CONFIGURED(HttpStatus.CONFLICT),
    CODE_HOST_INSTALL_STATE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
    CODE_HOST_INSTALLATION_FORBIDDEN(HttpStatus.FORBIDDEN),
    CODE_HOST_INSTALLATION_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    CodebaseError(HttpStatus status) {
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
