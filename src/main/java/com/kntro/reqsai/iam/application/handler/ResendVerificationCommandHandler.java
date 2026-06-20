package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ResendVerificationCommand;
import com.kntro.reqsai.iam.application.config.IamTokenProperties;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.EmailVerificationRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.EmailVerification;
import com.kntro.reqsai.shared.domain.support.TokenGenerator;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Resends the email-verification link for an account still in {@code PENDING_VERIFICATION}.
 * <p>
 * Flow: look up an account by email → if not found, return silently (anti-enumeration) →
 * if found but already active, throw {@code ACCOUNT_NOT_PENDING_VERIFICATION} (409) →
 * otherwise issue a new {@link EmailVerification} token (TTL 24 h) → persist →
 * send the verification email with the raw token.
 * The old token is not explicitly invalidated; it stays in the table and will simply fail
 * {@link EmailVerification#isValid} once it expires or after this new one is used first.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResendVerificationCommandHandler {

    private final AccountRepository accounts;
    private final EmailVerificationRepository emailVerifications;
    private final IamTokenProperties tokenProperties;

    @Transactional
    public void handle(ResendVerificationCommand command) {
        Optional<Account> maybeAccount = accounts.findByEmail(Email.of(command.email()));
        if (maybeAccount.isEmpty()) {
            log.debug("Resend-verification requested for unknown email — silently ignored");
            return;
        }

        Account account = maybeAccount.get();
        if (!account.isPendingVerification()) {
            throw IamExceptions.accountNotPendingVerification();
        }
        emailVerifications.deleteByAccountId(account.getId());

        String rawToken = TokenGenerator.generate(tokenProperties.tokenBytes());
        Instant expiresAt = Instant.now().plus(tokenProperties.emailVerificationExpiration());

        EmailVerification verification = EmailVerification.issue(account.getId(), rawToken, expiresAt);
        emailVerifications.save(verification);

        log.info("Verification email resent for account {}", account.getId());
    }

}
