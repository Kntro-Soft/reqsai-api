package com.kntro.reqsai.iam.domain.model;

import com.kntro.reqsai.shared.domain.model.AuditableEntity;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.shared.domain.support.IdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a single issued refresh token. Stored in {@code public.refresh_tokens}
 * (public schema — shared across tenants, linked to a user by {@code userId}).
 * <p>
 * The raw token is <em>never</em> persisted; only its SHA-256 hex digest ({@code tokenHash}) is stored
 * so a database breach cannot be replayed. The raw value is returned only at issuance time.
 * <p>
 * Lifecycle: {@code ACTIVE} → {@code REVOKED} (rotation or logout). {@code EXPIRED} is a maintenance
 * state set by a cleanup job — domain logic only cares about {@link #isValid(Instant)}.
 */
@Entity
@Table(name = "refresh_tokens", schema = "public")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends AuditableEntity {

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TokenStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Issues a new refresh token for the given user.
     *
     * @param userId     the user this token belongs to (stored as-is, not a FK join in domain layer)
     * @param rawToken   the 64-char random hex string to hash and store
     * @param expiresAt  absolute expiry instant
     * @return a persisted-ready aggregate with status {@code ACTIVE}
     */
    public static RefreshToken issue(UUID userId, String rawToken, Instant expiresAt) {
        RefreshToken rt = new RefreshToken(IdGenerator.newId());
        rt.tokenHash = HashUtils.sha256(rawToken);
        rt.userId = userId;
        rt.status = TokenStatus.ACTIVE;
        rt.expiresAt = expiresAt;
        return rt;
    }

    private RefreshToken(UUID id) {
        super(id);
    }

    /**
     * Returns {@code true} if this token is {@code ACTIVE} and has not yet reached its expiry instant.
     *
     * @param now the current instant to compare against
     */
    public boolean isValid(Instant now) {
        return status == TokenStatus.ACTIVE && expiresAt.isAfter(now);
    }

    /**
     * Revokes this token (rotation or logout).
     *
     * @param now the instant of revocation, stored in {@code revoked_at}
     */
    public void revoke(Instant now) {
        this.status = TokenStatus.REVOKED;
        this.revokedAt = now;
    }

}
