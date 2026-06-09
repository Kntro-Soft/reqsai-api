package com.kntro.reqsai.shared.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Cross-cutting error codes owned by the Shared Kernel.
 * <p>
 * Only generic concerns live here — validation, authentication/token, authorization, infrastructure
 * and the generic fallback. <strong>Bounded-context-specific</strong> codes (e.g.
 * {@code USER_NOT_FOUND}, {@code WORKSPACE_NAME_TAKEN}, {@code PLAN_LIMIT_EXCEEDED}) belong to that
 * context's own {@link ErrorCatalog} enum, not here.
 */
public enum CommonError implements ErrorCatalog {

    // ── Validation ─────────────────────────────── 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_FIELD(HttpStatus.BAD_REQUEST),
    INVALID_VALUE(HttpStatus.BAD_REQUEST),

    // ── Authentication ─────────────────────────── 401
    NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),

    // ── Authorization ──────────────────────────── 403
    PERMISSION_DENIED(HttpStatus.FORBIDDEN),
    TENANT_ACCESS_DENIED(HttpStatus.FORBIDDEN),

    // ── Infrastructure ─────────────────────────── 500/502
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    TENANT_PROVISIONING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY),
    AI_SERVICE_ERROR(HttpStatus.BAD_GATEWAY),

    // ── Generic ────────────────────────────────── 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    CommonError(HttpStatus status) {
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
