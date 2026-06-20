package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.VerifyEmailCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.EmailVerification;
import com.kntro.reqsai.iam.domain.port.out.EmailVerificationRepositoryPort;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class VerifyEmailCommandHandler {

    private final EmailVerificationRepositoryPort emailVerifications;
    private final AccountRepository accounts;

    @Transactional
    public void handle(VerifyEmailCommand command) {
        String hash = HashUtils.sha256(command.rawToken());
        EmailVerification verification = emailVerifications.findByTokenHash(hash)
                .orElseThrow(IamExceptions::invalidVerificationToken);

        Instant now = Instant.now();
        if (!verification.isValid(now)) {
            throw IamExceptions.invalidVerificationToken();
        }

        Account account = accounts.findById(verification.getAccountId())
                .orElseThrow(IamExceptions::invalidVerificationToken);

        account.activate();
        accounts.save(account);

        verification.markUsed(now);
        emailVerifications.save(verification);

        log.info("Email verified for account {}", account.getId());
    }
}
