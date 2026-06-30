package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.application.query.GetUserAvatarQuery;
import com.kntro.reqsai.shared.infrastructure.avatar.GeneratedAvatar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Loads a user's stored avatar bytes for the public serve endpoint. Returns an empty {@link Optional}
 * when the user is unknown or has no avatar — the endpoint then responds {@code 404}.
 */
@Component
@RequiredArgsConstructor
public class GetUserAvatarQueryHandler {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public Optional<GeneratedAvatar> handle(GetUserAvatarQuery query) {
        return users.findById(query.userId())
                .filter(user -> user.getAvatar() != null)
                .map(user -> new GeneratedAvatar(user.getAvatar(), user.getAvatarContentType()));
    }
}
