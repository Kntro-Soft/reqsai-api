package com.kntro.reqsai.iam.domain.exception;

import com.kntro.reqsai.shared.domain.exception.AuthenticationException;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

import java.util.UUID;

/**
 * Factory for IAM domain exceptions — the context-specific counterpart of the shared {@code Exceptions}.
 * Authentication failures return {@link AuthenticationException} (HTTP 401); not-found cases an
 * {@link EntityNotFoundException}; the rest a {@link DomainException} carrying an {@link IamError}.
 * <p>
 * {@code invalidCredentials} is deliberately generic (it never says whether the email or the password was
 * wrong) to avoid leaking which accounts exist.
 */
public final class IamExceptions {

    private IamExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static DomainException accountAlreadyExists(String email) {
        return new DomainException(IamError.ACCOUNT_ALREADY_EXISTS,
                "An account already exists for email: " + email);
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException(IamError.INVALID_CREDENTIALS, "Invalid email or password");
    }

    public static AuthenticationException invalidRefreshToken() {
        return new AuthenticationException(IamError.INVALID_REFRESH_TOKEN,
                "Refresh token is invalid or has expired");
    }

    public static DomainException accountNotActive() {
        return new DomainException(IamError.ACCOUNT_NOT_ACTIVE, "Account is not active");
    }

    public static AuthenticationException invalidVerificationToken() {
        return new AuthenticationException(IamError.INVALID_VERIFICATION_TOKEN, "Verification token is invalid or has expired");
    }

    public static EntityNotFoundException userNotFound(UUID id) {
        return new EntityNotFoundException(IamError.USER_NOT_FOUND, "User not found: " + id);
    }

    public static DomainException organizationNotOwned(UUID organizationId) {
        return new DomainException(IamError.ORGANIZATION_NOT_OWNED,
                "Organization " + organizationId + " is not owned by the current user");
    }

    public static DomainException cannotSuspendAccount(UUID accountId) {
        return new DomainException(IamError.CANNOT_SUSPEND_ACCOUNT,
                "Cannot suspend account " + accountId + ": account is already deleted");
    }

    public static AuthenticationException invalidPasswordResetToken() {
        return new AuthenticationException(IamError.INVALID_PASSWORD_RESET_TOKEN,
                "Password reset token is invalid or has expired");
    }

    public static AuthenticationException invalidCurrentPassword() {
        return new AuthenticationException(IamError.INVALID_CURRENT_PASSWORD, "Current password is incorrect");
    }

    public static DomainException accountNotPendingVerification() {
        return new DomainException(IamError.ACCOUNT_NOT_PENDING_VERIFICATION,
                "Account is not pending verification — email may already be verified");
    }
}
