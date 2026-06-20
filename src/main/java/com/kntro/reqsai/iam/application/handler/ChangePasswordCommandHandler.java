package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ChangePasswordCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.PasswordHasher;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the password for the authenticated user after verifying the current one.
 * <p>
 * Flow: load {@link User} → load linked {@link Account} → verify current password → hash new password
 * → call {@link Account#changePassword} → persist. Throws {@code INVALID_CURRENT_PASSWORD} (401)
 * if the current password does not match.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChangePasswordCommandHandler {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;

    @Transactional
    public void handle(ChangePasswordCommand command) {
        User user = users.findById(command.userId())
                .orElseThrow(() -> IamExceptions.userNotFound(command.userId()));

        Account account = accounts.findById(user.getAccountId())
                .orElseThrow(() -> IamExceptions.userNotFound(command.userId()));

        if (!passwordHasher.matches(command.currentPassword(), account.getPasswordHash())) {
            throw IamExceptions.invalidCurrentPassword();
        }

        account.changePassword(passwordHasher.hash(command.newPassword()));
        accounts.save(account);

        log.info("Password changed for account {}", account.getId());
    }
}
