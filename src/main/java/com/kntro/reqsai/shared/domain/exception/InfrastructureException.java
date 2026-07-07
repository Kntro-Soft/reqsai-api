package com.kntro.reqsai.shared.domain.exception;

/**
 * Failure in an external/infrastructure dependency (database, AI service, third-party API).
 * <p>
 * Always wraps the original cause; the handler logs it at {@code error} (with stacktrace) and never
 * exposes the internal message to the client.
 */
public class InfrastructureException extends DomainException {

    public InfrastructureException(ErrorCatalog error, String message, Throwable cause) {
        super(error, message, cause);
    }
}
