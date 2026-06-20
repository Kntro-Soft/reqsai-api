package com.kntro.reqsai.iam.infrastructure.persistence.repositories;

import com.kntro.reqsai.iam.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link RefreshToken}.
 * Backed by {@code public.refresh_tokens}; queried only through
 * {@code RefreshTokenRepositoryAdapter}.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff OR r.revokedAt IS NOT NULL")
    void deleteExpiredOrRevokedBefore(Instant cutoff);
}
