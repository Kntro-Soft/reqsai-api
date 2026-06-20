package com.kntro.reqsai.iam.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.domain.model.RefreshToken;
import com.kntro.reqsai.iam.domain.port.out.RefreshTokenRepositoryPort;
import com.kntro.reqsai.iam.infrastructure.persistence.repositories.RefreshTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link RefreshTokenRepositoryPort} domain port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepositoryPort {

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
}
