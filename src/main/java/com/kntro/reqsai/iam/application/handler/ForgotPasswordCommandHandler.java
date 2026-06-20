package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ForgotPasswordCommand;
import com.kntro.reqsai.iam.application.config.IamTokenProperties;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.shared.domain.support.TokenGenerator;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Initiates the password-reset flow by generating a one-time token and sending it to the user's inbox.
 * <p>
 * Flow: look up an account by email → if not found or not active, return silently (no enumeration) →
 * generate 32-byte secure random token → store its SHA-256 hash in the account with a 1-hour TTL →
 * load the user's first name → send the reset email with the raw token.
 * Transactional so the token and its expiry are persisted before the email is sent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordCommandHandler {

    private final AccountRepository accounts;
    private final IamTokenProperties tokenProperties;

    @Transactional
    public void handle(ForgotPasswordCommand command) {
        Optional<Account> maybeAccount = accounts.findByEmail(Email.of(command.email()));
        if (maybeAccount.isEmpty() || !maybeAccount.get().isActive()) {
            log.debug("Forgot-password requested for unknown or inactive email — silently ignored");
            return;
        }

        Account account = maybeAccount.get();
        String rawToken = TokenGenerator.generate(tokenProperties.tokenBytes());
        String tokenHash = HashUtils.sha256(rawToken);
        Instant expiresAt = Instant.now().plus(tokenProperties.passwordResetExpiration());

        account.generatePasswordResetToken(rawToken, tokenHash, expiresAt);
        accounts.save(account);

        log.info("Password reset token issued for account {}", account.getId());
    }

}
