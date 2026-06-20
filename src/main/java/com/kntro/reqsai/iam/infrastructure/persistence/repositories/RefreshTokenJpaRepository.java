package com.kntro.reqsai.iam.infrastructure.persistence.repositories;

import com.kntro.reqsai.iam.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
