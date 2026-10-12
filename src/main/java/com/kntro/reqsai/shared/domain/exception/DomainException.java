package com.kntro.reqsai.shared.domain.exception;

import java.util.Map;

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
    private final transient Map<String, Object> details;

    public DomainException(ErrorCatalog error, String message) {
        super(message);
        this.error = error;
        this.details = Map.of();
    }

    /** As above, with machine-readable facts the error response carries next to its code (e.g. a limit). */
    public DomainException(ErrorCatalog error, String message, Map<String, Object> details) {
        super(message);
        this.error = error;
        this.details = Map.copyOf(details);
    }

    public DomainException(ErrorCatalog error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
        this.details = Map.of();
    }

    public ErrorCatalog error() {
        return error;
    }

    /** Extra properties of the error response, empty for most errors. */
    public Map<String, Object> details() {
        return details;
    }
}
