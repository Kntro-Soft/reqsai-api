package com.kntro.reqsai.iam.interfaces.rest.controllers;

import com.kntro.reqsai.iam.application.command.ForgotPasswordCommand;
import com.kntro.reqsai.iam.application.command.ResendVerificationCommand;
import com.kntro.reqsai.iam.application.command.ResetPasswordCommand;
import com.kntro.reqsai.iam.application.command.VerifyEmailCommand;
import com.kntro.reqsai.iam.application.handler.AuthenticateCommandHandler;
import com.kntro.reqsai.iam.application.handler.ForgotPasswordCommandHandler;
import com.kntro.reqsai.iam.application.handler.RefreshSessionCommandHandler;
import com.kntro.reqsai.iam.application.handler.RegisterAccountCommandHandler;
import com.kntro.reqsai.iam.application.handler.ResendVerificationCommandHandler;
import com.kntro.reqsai.iam.application.handler.ResetPasswordCommandHandler;
import com.kntro.reqsai.iam.application.handler.RevokeRefreshTokenCommandHandler;
import com.kntro.reqsai.iam.application.handler.VerifyEmailCommandHandler;
import com.kntro.reqsai.iam.application.result.AuthenticatedSession;
import com.kntro.reqsai.iam.application.result.RefreshedSession;
import com.kntro.reqsai.iam.application.result.UserProfile;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.ForgotPasswordRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.LoginRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.RegisterRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.ResendVerificationRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.ResetPasswordRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.VerifyEmailRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.AuthResponse;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserResponse;
import com.kntro.reqsai.iam.interfaces.rest.mappers.request.AuthRequestMapper;
import com.kntro.reqsai.iam.interfaces.rest.mappers.response.AuthResponseMapper;
import com.kntro.reqsai.iam.interfaces.rest.mappers.response.UserResponseMapper;
import com.kntro.reqsai.iam.interfaces.rest.swagger.AuthController;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Implementation of the {@link AuthController} API contract. */
@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements AuthController {

    private static final String REFRESH_COOKIE_NAME = "rt";
    private static final String COOKIE_PATH = "/api/auth";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(30);

    private final RegisterAccountCommandHandler registerAccount;
    private final AuthenticateCommandHandler authenticate;
    private final RefreshSessionCommandHandler refreshSession;
    private final RevokeRefreshTokenCommandHandler revokeRefreshToken;
    private final VerifyEmailCommandHandler verifyEmail;
    private final ForgotPasswordCommandHandler forgotPassword;
    private final ResetPasswordCommandHandler resetPassword;
    private final ResendVerificationCommandHandler resendVerification;

    @Override
    public ResponseEntity<UserResponse> signUp(RegisterRequest request) {
        UserProfile profile = registerAccount.handle(AuthRequestMapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseMapper.toResponse(profile));
    }

    @Override
    public ResponseEntity<AuthResponse> signIn(LoginRequest request, HttpServletResponse response) {
        AuthenticatedSession session = authenticate.handle(AuthRequestMapper.toCommand(request));
        setRefreshCookie(response, session.rawRefreshToken());
        return ResponseEntity.ok(AuthResponseMapper.toResponse(session));
    }

    @Override
    public ResponseEntity<AuthResponse> refresh(String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RefreshedSession session = refreshSession.handle(rawRefreshToken);
        setRefreshCookie(response, session.rawRefreshToken());
        return ResponseEntity.ok(AuthResponseMapper.toResponse(session));
    }

    @Override
    public ResponseEntity<Void> signOut(String rawRefreshToken, HttpServletResponse response) {
        revokeRefreshToken.handle(rawRefreshToken);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> verifyEmail(VerifyEmailRequest request) {
        verifyEmail.handle(new VerifyEmailCommand(request.token()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> forgotPassword(ForgotPasswordRequest request) {
        forgotPassword.handle(new ForgotPasswordCommand(request.email()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resetPassword(ResetPasswordRequest request) {
        resetPassword.handle(new ResetPasswordCommand(request.token(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resendVerification(ResendVerificationRequest request) {
        resendVerification.handle(new ResendVerificationCommand(request.email()));
        return ResponseEntity.noContent().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(REFRESH_COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
