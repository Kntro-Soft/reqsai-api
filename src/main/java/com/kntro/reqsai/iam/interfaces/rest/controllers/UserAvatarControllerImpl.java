package com.kntro.reqsai.iam.interfaces.rest.controllers;

import com.kntro.reqsai.iam.application.handler.GetUserAvatarQueryHandler;
import com.kntro.reqsai.iam.application.query.GetUserAvatarQuery;
import com.kntro.reqsai.iam.interfaces.rest.swagger.UserAvatarController;
import com.kntro.reqsai.shared.interfaces.rest.AvatarResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link UserAvatarController} API contract (public). */
@RestController
@RequiredArgsConstructor
public class UserAvatarControllerImpl implements UserAvatarController {

    private final GetUserAvatarQueryHandler getUserAvatar;

    @Override
    public ResponseEntity<byte[]> getAvatar(UUID userId) {
        return AvatarResponses.of(getUserAvatar.handle(new GetUserAvatarQuery(userId)));
    }
}
