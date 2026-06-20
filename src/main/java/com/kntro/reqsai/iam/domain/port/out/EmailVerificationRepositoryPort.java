package com.kntro.reqsai.iam.domain.port.out;

import com.kntro.reqsai.iam.domain.model.EmailVerification;

import java.util.Optional;

public interface EmailVerificationRepositoryPort {
    void save(EmailVerification emailVerification);
    Optional<EmailVerification> findByTokenHash(String tokenHash);
}
