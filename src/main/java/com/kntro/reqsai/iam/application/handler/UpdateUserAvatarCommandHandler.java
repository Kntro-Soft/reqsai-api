package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.UpdateUserAvatarCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.application.result.UserProfile;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the authenticated user's avatar with an uploaded image.
 * <p>
 * Flow: load {@link User} by id → apply the uploaded bytes via {@link User#applyAvatar} → persist →
 * return the updated profile (with its account email).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateUserAvatarCommandHandler {

    private final UserRepository users;
    private final AccountRepository accounts;

    @Transactional
    public UserProfile handle(UpdateUserAvatarCommand command) {
        User user = users.findById(command.userId())
                .orElseThrow(() -> IamExceptions.userNotFound(command.userId()));

        user.applyAvatar(command.bytes(), command.contentType());
        users.save(user);

        Account account = accounts.findById(user.getAccountId())
                .orElseThrow(() -> IamExceptions.userNotFound(command.userId()));

        log.info("Avatar updated for user {}", user.getId());
        return new UserProfile(user, account.getEmail().value());
    }
}
