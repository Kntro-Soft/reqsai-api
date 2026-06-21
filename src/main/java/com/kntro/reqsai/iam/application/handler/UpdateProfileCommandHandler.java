package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.UpdateProfileCommand;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates the editable profile fields (first name, last name, avatar URL) for the authenticated user.
 * <p>
 * Flow: load {@link User} by id → apply {@link User#updateProfile} → persist → return updated user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateProfileCommandHandler {

    private final UserRepository users;

    @Transactional
    public User handle(UpdateProfileCommand command) {
        User user = users.findById(command.userId())
                .orElseThrow(() -> IamExceptions.userNotFound(command.userId()));

        user.updateProfile(command.firstName(), command.lastName(), command.avatarUrl());
        users.save(user);

        log.info("Profile updated for user {}", user.getId());
        return user;
    }
}
