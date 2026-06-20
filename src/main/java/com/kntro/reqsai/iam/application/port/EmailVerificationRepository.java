package com.kntro.reqsai.iam.application.port;

import com.kntro.reqsai.iam.domain.model.EmailVerification;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link EmailVerification} aggregate.
 * Implemented by {@code EmailVerificationRepositoryAdapter} in the infrastructure layer.
 */
public interface EmailVerificationRepository {

    /** Persists a new or updated {@link EmailVerification}. */
    void save(EmailVerification emailVerification);

    /**
     * Looks up a verification record by its SHA-256 token hash.
     *
     * @param tokenHash the SHA-256 hex digest of the raw one-time token
     * @return the matching record, or {@link Optional#empty()} if not found or already used
     */
    Optional<EmailVerification> findByTokenHash(String tokenHash);

    /**
     * Deletes all verification tokens for the given account.
     * Called before issuing a new token on resend so stale tokens cannot be used.
     */
    void deleteByAccountId(UUID accountId);
}
