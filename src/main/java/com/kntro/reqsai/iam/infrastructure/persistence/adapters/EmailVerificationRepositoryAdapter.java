package com.kntro.reqsai.iam.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.domain.model.EmailVerification;
import com.kntro.reqsai.iam.application.port.EmailVerificationRepository;
import com.kntro.reqsai.iam.infrastructure.persistence.repositories.EmailVerificationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link com.kntro.reqsai.iam.application.port.EmailVerificationRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class EmailVerificationRepositoryAdapter implements EmailVerificationRepository {

    private final EmailVerificationJpaRepository jpa;

    @Override
    public void save(EmailVerification emailVerification) {
        jpa.save(emailVerification);
    }

    @Override
    public Optional<EmailVerification> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash);
    }

    @Override
    public void deleteByAccountId(UUID accountId) {
        jpa.deleteByAccountId(accountId);
    }
}
