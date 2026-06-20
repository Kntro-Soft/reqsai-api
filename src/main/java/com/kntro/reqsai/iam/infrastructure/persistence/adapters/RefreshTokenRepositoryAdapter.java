package com.kntro.reqsai.iam.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.domain.model.RefreshToken;
import com.kntro.reqsai.iam.application.port.RefreshTokenRepository;
import com.kntro.reqsai.iam.infrastructure.persistence.repositories.RefreshTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link RefreshTokenRepository} domain port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;

    @Override
    public void save(RefreshToken token) {
        jpa.save(token);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String hash) {
        return jpa.findByTokenHash(hash);
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpa.deleteByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteExpiredOrRevokedBefore(Instant cutoff) {
        jpa.deleteExpiredOrRevokedBefore(cutoff);
    }
}
