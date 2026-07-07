package com.kntro.reqsai.iam.interfaces.rest.controllers;

import com.kntro.reqsai.iam.application.command.UpdateUserAvatarCommand;
import com.kntro.reqsai.iam.application.handler.GetUserAvatarQueryHandler;
import com.kntro.reqsai.iam.application.handler.UpdateUserAvatarCommandHandler;
import com.kntro.reqsai.iam.application.query.GetUserAvatarQuery;
import com.kntro.reqsai.iam.application.result.UserProfile;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserResponse;
import com.kntro.reqsai.iam.interfaces.rest.mappers.response.UserResponseMapper;
import com.kntro.reqsai.iam.interfaces.rest.swagger.UserAvatarController;
import com.kntro.reqsai.shared.application.avatar.GeneratedAvatar;
import com.kntro.reqsai.shared.interfaces.rest.AvatarResponses;
import com.kntro.reqsai.shared.interfaces.rest.AvatarUploads;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Implementation of the {@link UserAvatarController} API contract (public GET, authenticated PUT). */
@RestController
@RequiredArgsConstructor
public class UserAvatarControllerImpl implements UserAvatarController {

    private final GetUserAvatarQueryHandler getUserAvatar;
    private final UpdateUserAvatarCommandHandler updateUserAvatar;

    @Override
    public ResponseEntity<byte[]> getAvatar(UUID userId) {
        return AvatarResponses.of(getUserAvatar.handle(new GetUserAvatarQuery(userId)));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> uploadAvatar(MultipartFile file, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        GeneratedAvatar avatar = AvatarUploads.validated(file);
        UserProfile profile = updateUserAvatar.handle(new UpdateUserAvatarCommand(userId, avatar.bytes(), avatar.contentType()));
        return ResponseEntity.ok(UserResponseMapper.toResponse(profile));
    }
}
