package com.kntro.reqsai.shared.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Contract for an error code: a stable textual {@code code} plus the {@link HttpStatus} it maps to.
 * <p>
 * This is the extension point that keeps error handling decoupled in a modular monolith. The Shared
 * Kernel only defines {@link CommonError} (cross-cutting codes); <strong>each bounded context defines
 * its own enum</strong> implementing this interface (e.g. {@code IamError}, {@code WorkspaceError}),
 * so adding a context-specific error never touches {@code shared}. The
 * {@link com.kntro.reqsai.shared.infrastructure.web.GlobalExceptionHandler} only needs
 * {@link #code()} and {@link #status()}, so it works with any catalog.
 *
 * <pre>{@code
 * // in iam/domain/exception
 * public enum IamError implements ErrorCatalog {
 *     USER_NOT_FOUND(HttpStatus.NOT_FOUND),
 *     EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT);
 *     private final HttpStatus status;
 *     IamError(HttpStatus status) { this.status = status; }
 *     public String code()       { return name(); }
 *     public HttpStatus status() { return status; }
 * }
 * }</pre>
 */
public interface ErrorCatalog {

    /** Stable, machine-readable code (typically the enum {@code name()}). */
    String code();

    /** HTTP status this error maps to. */
    HttpStatus status();
}
