package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.VerifyEmailCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.EmailVerification;
import com.kntro.reqsai.iam.application.port.EmailVerificationRepository;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Verifies an account's email address using the one-time token delivered to the user's inbox.
 * <p>
 * Flow: hash the raw token → look up the {@link com.kntro.reqsai.iam.domain.model.EmailVerification}
 * → validate it is unused and not expired → load the linked {@link com.kntro.reqsai.iam.domain.model.Account}
 * → activate the account (raises {@code AccountVerifiedEvent}) → mark the token as used.
 * Transactional so both saves land together.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerifyEmailCommandHandler {

    private final EmailVerificationRepository emailVerifications;
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
