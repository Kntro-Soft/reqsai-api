package com.kntro.reqsai.iam.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.domain.model.EmailVerification;
import com.kntro.reqsai.iam.domain.port.out.EmailVerificationRepositoryPort;
import com.kntro.reqsai.iam.infrastructure.persistence.repositories.EmailVerificationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepositoryAdapter implements EmailVerificationRepositoryPort {

    private final EmailVerificationJpaRepository jpa;

    @Override
    public void save(EmailVerification emailVerification) {
        jpa.save(emailVerification);
    }

    @Override
    public Optional<EmailVerification> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash);
    }
}
