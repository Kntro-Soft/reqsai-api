package com.kntro.reqsai.shared.domain.exception;

/**
 * Base class for all domain/application errors.
 * <p>
 * Carries an {@link ErrorCatalog} (which knows its code and HTTP status). Most errors use this class
 * directly; subclasses exist only where the {@code GlobalExceptionHandler} needs to react
 * differently. The {@code ErrorCatalog} may come from {@link CommonError} (shared) or from any
 * bounded context's own catalog.
 */
public class DomainException extends RuntimeException {

    private final transient ErrorCatalog error;

    public DomainException(ErrorCatalog error, String message) {
        super(message);
        this.error = error;
    }

    public DomainException(ErrorCatalog error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public ErrorCatalog error() {
        return error;
    }
}
