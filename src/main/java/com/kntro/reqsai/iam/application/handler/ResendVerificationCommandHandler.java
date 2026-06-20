package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ResendVerificationCommand;
import com.kntro.reqsai.iam.application.config.IamTokenProperties;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.EmailVerificationRepository;
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
 * Flow: look up an account by email → if not found or not pending, return silently (no enumeration) →
 * issue a new {@link EmailVerification} token (TTL 24 h) → persist → load user's first name →
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
        if (maybeAccount.isEmpty() || !maybeAccount.get().isPendingVerification()) {
            log.debug("Resend-verification requested for unknown or already-verified email — silently ignored");
            return;
        }

        Account account = maybeAccount.get();
        emailVerifications.deleteByAccountId(account.getId());

        String rawToken = TokenGenerator.generate(tokenProperties.tokenBytes());
        Instant expiresAt = Instant.now().plus(tokenProperties.emailVerificationExpiration());

        EmailVerification verification = EmailVerification.issue(account.getId(), rawToken, expiresAt);
        emailVerifications.save(verification);

        log.info("Verification email resent for account {}", account.getId());
    }

}
