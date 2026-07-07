package com.kntro.reqsai.shared.domain.exception;

/**
 * Factory for <strong>cross-cutting</strong> domain exceptions owned by the Shared Kernel.
 * <p>
 * Only generic helpers live here (validation, token, infrastructure). Bounded-context-specific
 * factories (e.g. {@code userNotFound}, {@code workspaceNameTaken}, {@code planLimitExceeded}) belong
 * to each context — define a small {@code XxxExceptions} class in that context's {@code domain},
 * or throw {@code new DomainException(XxxError.CODE, message)} directly. This keeps {@code shared}
 * free of every context's vocabulary.
 */
public final class Exceptions {

    private Exceptions() {
    }

    // Validation
    public static DomainException invalidField(String field, String reason) {
        return new DomainException(CommonError.INVALID_FIELD,
                "Invalid '%s': %s".formatted(field, reason));
    }

    public static DomainException invalidValue(String what, String reason) {
        return new DomainException(CommonError.INVALID_VALUE,
                "Invalid %s: %s".formatted(what, reason));
    }

    // Authentication
    public static AuthenticationException tokenExpired() {
        return new AuthenticationException(CommonError.TOKEN_EXPIRED,
                "Authentication token has expired");
    }

    public static AuthenticationException tokenInvalid() {
        return new AuthenticationException(CommonError.TOKEN_INVALID,
                "Authentication token is invalid");
    }

    // Infrastructure
    public static InfrastructureException databaseError(Throwable cause) {
        return new InfrastructureException(CommonError.DATABASE_ERROR,
                "A database error occurred", cause);
    }

    public static InfrastructureException tenantProvisioningFailed(String slug, Throwable cause) {
        return new InfrastructureException(CommonError.TENANT_PROVISIONING_FAILED,
                "Failed to provision tenant '%s'".formatted(slug), cause);
    }

    public static InfrastructureException aiServiceError(String detail, Throwable cause) {
        return new InfrastructureException(CommonError.AI_SERVICE_ERROR,
                "AI service unavailable: " + detail, cause);
    }
}
