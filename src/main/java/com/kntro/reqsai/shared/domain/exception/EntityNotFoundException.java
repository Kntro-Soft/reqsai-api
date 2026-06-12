package com.kntro.reqsai.shared.domain.exception;

/**
 * An entity could not be found (HTTP 404 via its {@link ErrorCatalog}).
 * A distinct type so the handler can log it at {@code warn} without a stacktrace.
 */
public class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(ErrorCatalog error, String message) {
        super(error, message);
    }
}
