package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ResetPasswordCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.PasswordHasher;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Applies a password reset using the one-time token from the forgot-password flow.
 * <p>
 * Flow: hash the raw token → look up the {@link Account} by token hash → delegate validation and
 * password replacement to {@link Account#resetPassword} (which enforces expiry and clears the token
 * on success) → persist. Throws {@code INVALID_PASSWORD_RESET_TOKEN} (401) for any invalid state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResetPasswordCommandHandler {

    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;

    @Transactional
    public void handle(ResetPasswordCommand command) {
        String tokenHash = HashUtils.sha256(command.rawToken());

        Account account = accounts.findByPasswordResetToken(tokenHash)
                .orElseThrow(IamExceptions::invalidPasswordResetToken);

        account.resetPassword(passwordHasher.hash(command.newPassword()), tokenHash, Instant.now());
        accounts.save(account);

        log.info("Password reset completed for account {}", account.getId());
    }
}
