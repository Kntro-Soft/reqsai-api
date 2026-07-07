package com.kntro.reqsai.iam.infrastructure.persistence.repositories;

import com.kntro.reqsai.iam.domain.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EmailVerification}.
 * Backed by {@code public.email_verifications}; queried only through
 * {@code EmailVerificationRepositoryAdapter}.
 */
public interface EmailVerificationJpaRepository extends JpaRepository<EmailVerification, UUID> {
    Optional<EmailVerification> findByTokenHash(String tokenHash);

    void deleteByAccountId(UUID accountId);

    @Modifying
    @Query("DELETE FROM EmailVerification e WHERE e.expiresAt < :cutoff")
    void deleteExpiredBefore(Instant cutoff);
}
