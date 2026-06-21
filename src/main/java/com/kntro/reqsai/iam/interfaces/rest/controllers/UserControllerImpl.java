package com.kntro.reqsai.iam.interfaces.rest.controllers;

import com.kntro.reqsai.iam.application.command.AcceptTermsCommand;
import com.kntro.reqsai.iam.application.command.ChangePasswordCommand;
import com.kntro.reqsai.iam.application.command.UpdateProfileCommand;
import com.kntro.reqsai.iam.application.command.UpdateUserPreferencesCommand;
import com.kntro.reqsai.iam.application.handler.AcceptTermsCommandHandler;
import com.kntro.reqsai.iam.application.handler.ChangePasswordCommandHandler;
import com.kntro.reqsai.iam.application.handler.GetAuthenticatedUserQueryHandler;
import com.kntro.reqsai.iam.application.handler.UpdateProfileCommandHandler;
import com.kntro.reqsai.iam.application.handler.UpdateUserPreferencesCommandHandler;
import com.kntro.reqsai.iam.application.query.GetAuthenticatedUserQuery;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.AcceptTermsRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.ChangePasswordRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.UpdatePreferencesRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.UpdateProfileRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserResponse;
import com.kntro.reqsai.iam.interfaces.rest.mappers.response.UserResponseMapper;
import com.kntro.reqsai.iam.interfaces.rest.swagger.UserController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link UserController} API contract. */
@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements UserController {

    private final GetAuthenticatedUserQueryHandler getAuthenticatedUser;
    private final AcceptTermsCommandHandler acceptTermsHandler;
    private final UpdateUserPreferencesCommandHandler updateUserPreferences;
    private final UpdateProfileCommandHandler updateProfile;
    private final ChangePasswordCommandHandler changePassword;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = getAuthenticatedUser.handle(new GetAuthenticatedUserQuery(userId));
        return ResponseEntity.ok(UserResponseMapper.toResponse(user));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> acceptTerms(AcceptTermsRequest request, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        acceptTermsHandler.handle(new AcceptTermsCommand(userId, request.termsVersion()));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> updatePreferences(UpdatePreferencesRequest request, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = updateUserPreferences.handle(new UpdateUserPreferencesCommand(userId, request.lastVisitedOrgId()));
        return ResponseEntity.ok(UserResponseMapper.toResponse(user));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> updateProfile(UpdateProfileRequest request, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = updateProfile.handle(new UpdateProfileCommand(userId, request.firstName(), request.lastName(), request.avatarUrl()));
        return ResponseEntity.ok(UserResponseMapper.toResponse(user));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(ChangePasswordRequest request, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        changePassword.handle(new ChangePasswordCommand(userId, request.currentPassword(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }
}
