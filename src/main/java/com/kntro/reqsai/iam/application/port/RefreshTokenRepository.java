package com.kntro.reqsai.iam.application.port;

import com.kntro.reqsai.iam.domain.model.RefreshToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link RefreshToken} aggregate.
 * Implemented by {@code RefreshTokenRepositoryAdapter} in the infrastructure layer.
 */
public interface RefreshTokenRepository {

    /** Persists a new or updated {@link RefreshToken}. */
    void save(RefreshToken token);

    /**
     * Looks up a refresh token by its SHA-256 hash.
     *
     * @param hash the SHA-256 hex digest of the raw refresh token
     * @return the matching token, or {@link Optional#empty()} if not found
     */
    Optional<RefreshToken> findByTokenHash(String hash);

    /**
     * Revokes all refresh tokens belonging to the given user. Used for sign-out-from-all-devices.
     *
     * @param userId the user whose tokens should be purged
     */
    void deleteByUserId(UUID userId);
}
