package com.kntro.reqsai.iam.domain.model;

import com.kntro.reqsai.iam.domain.event.AccountCreatedEvent;
import com.kntro.reqsai.iam.domain.event.AccountVerifiedEvent;
import com.kntro.reqsai.iam.domain.event.PasswordResetRequestedEvent;
import com.kntro.reqsai.iam.domain.event.TermsAcceptedEvent;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.infrastructure.persistence.converters.EmailConverter;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Aggregate root holding a user's credentials and account lifecycle. Lives in the global
 * {@code public.accounts} registry (identity is shared across tenants), keyed by a unique
 * {@link Email}.
 * <p>
 * Lifecycle: {@code PENDING_VERIFICATION} → {@code ACTIVE} (after email verified) → optionally
 * {@code SUSPENDED} or {@code DELETED} by an administrator. Only {@link #isActive()} accounts
 * may authenticate.
 * <p>
 * The password-reset state is embedded here. Email verification tokens live in a separate
 * {@code email_verifications} aggregate so one account can have at most one pending token
 * without overwriting the previous one.
 */
@Entity
@Table(name = "accounts", schema = "public")
@Getter
public class Account extends AggregateRoot {

    private static final int PASSWORD_HASH_MAX = 255;
    private static final int RESET_TOKEN_MAX = 64;
    private static final int TERMS_VERSION_MAX = 50;

    @Convert(converter = EmailConverter.class)
    @Column(name = "email", nullable = false, unique = true, length = 320, updatable = false)
    private Email email;

    @Column(name = "password_hash", nullable = false, length = PASSWORD_HASH_MAX)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountStatus status;

    @Nullable
    @Column(name = "password_reset_token", length = RESET_TOKEN_MAX)
    private String passwordResetToken;

    @Nullable
    @Column(name = "password_reset_token_expires_at")
    private Instant passwordResetTokenExpiresAt;

    @Nullable
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Nullable
    @Column(name = "terms_version", length = TERMS_VERSION_MAX)
    private String termsVersion;

    protected Account() {
        super();
    }

    private Account(Email email, String passwordHash, AccountStatus status) {
        super();
        this.email = Assert.notNull(email, "email");
        this.passwordHash = Assert.maxLength(Assert.notBlank(passwordHash, "passwordHash"), "passwordHash", PASSWORD_HASH_MAX);
        this.status = Assert.notNull(status, "status");
    }

    /**
     * Registers a new account in {@code PENDING_VERIFICATION} state and raises {@link AccountCreatedEvent}.
     *
     * @param email unique account email (already validated/normalized)
     * @param passwordHash BCrypt hash of the password (never the clear text)
     */
    public static Account register(Email email, String passwordHash) {
        Account account = new Account(email, passwordHash, AccountStatus.PENDING_VERIFICATION);
        account.registerEvent(AccountCreatedEvent.of(account.getId(), email.value()));
        return account;
    }

    /** Activates the account after successful email verification and raises {@link AccountVerifiedEvent}. */
    public void activate() {
        this.status = AccountStatus.ACTIVE;
        registerEvent(AccountVerifiedEvent.of(getId(), email.value()));
    }

    /** Suspends the account. Suspended accounts cannot be authenticated. */
    public void suspend() {
        if (isDeleted()) throw IamExceptions.cannotSuspendAccount(getId());
        this.status = AccountStatus.SUSPENDED;
    }

    /** Soft-deletes the account. Cannot be recovered through standard flows. */
    public void delete() {
        this.status = AccountStatus.DELETED;
    }

    /** Replaces the stored password hash. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = Assert.maxLength(Assert.notBlank(newPasswordHash, "passwordHash"), "passwordHash", PASSWORD_HASH_MAX);
    }

    /**
     * Stores a password-reset token hash and raises {@link PasswordResetRequestedEvent} so the
     * notification listener can send the email with the raw token after commit.
     *
     * @param rawToken unhashed token — never persisted, only carried in the domain event
     * @param tokenHash SHA-256 hex digest of {@code rawToken} — the value actually stored
     * @param expiresAt when the token becomes invalid
     */
    public void generatePasswordResetToken(String rawToken, String tokenHash, Instant expiresAt) {
        this.passwordResetToken = Assert.maxLength(Assert.notBlank(tokenHash, "tokenHash"), "tokenHash", RESET_TOKEN_MAX);
        this.passwordResetTokenExpiresAt = Assert.notNull(expiresAt, "expiresAt");
        registerEvent(PasswordResetRequestedEvent.of(getId(), rawToken));
    }

    /**
     * Validates the provided token hash against the stored one and, if valid, replaces
     * the password and clears the reset token.
     */
    public void resetPassword(String newPasswordHash, String tokenHash, Instant now) {
        if (passwordResetToken == null
                || passwordResetTokenExpiresAt == null
                || !passwordResetToken.equals(tokenHash)
                || now.isAfter(passwordResetTokenExpiresAt)) {
            throw IamExceptions.invalidPasswordResetToken();
        }
        this.passwordHash = Assert.maxLength(Assert.notBlank(newPasswordHash, "passwordHash"), "passwordHash", PASSWORD_HASH_MAX);
        this.passwordResetToken = null;
        this.passwordResetTokenExpiresAt = null;
    }

    /** Records that the user has accepted a specific T&C version and raises {@link TermsAcceptedEvent}. */
    public void acceptTerms(String version, Instant now) {
        this.termsVersion = Assert.maxLength(Assert.notBlank(version, "version"), "version", TERMS_VERSION_MAX);
        this.termsAcceptedAt = Assert.notNull(now, "now");
        registerEvent(TermsAcceptedEvent.of(getId(), this.termsVersion));
    }

    public boolean isPendingVerification() { return status == AccountStatus.PENDING_VERIFICATION; }

    public boolean isActive() { return status == AccountStatus.ACTIVE; }

    public boolean isSuspended() { return status == AccountStatus.SUSPENDED; }

    public boolean isDeleted() { return status == AccountStatus.DELETED; }
}
