package com.kntro.reqsai.iam.interfaces.rest.swagger;

import com.kntro.reqsai.iam.interfaces.rest.dto.request.ForgotPasswordRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.LoginRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.RegisterRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.ResendVerificationRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.ResetPasswordRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.VerifyEmailRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.AuthResponse;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseConflict;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseUnauthorized;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * API contract (OpenAPI documentation) for authentication endpoints. The implementation lives in
 * {@code controllers.AuthControllerImpl}; keeping the annotations here leaves the controller free of
 * documentation noise.
 * <p>
 * User profile management endpoints (GET/PATCH/PUT /users/me) live in {@link UserController}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Account registration, login, token refresh and logout")
public interface AuthController {

    @Operation(
            summary = "Register a new account",
            description = """
                    Creates an account (with its user profile) and returns the user. The account is created \
                    PENDING_VERIFICATION; the user must verify their email before logging in via `POST /api/auth/login`.""")
    @ApiResponse(
            responseCode = "201",
            description = "Account registered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseConflict
    @ApiStandardErrorResponses
    @PostMapping(path = "/register", version = ApiVersioning.V1)
    ResponseEntity<UserResponse> signUp(@Valid @RequestBody RegisterRequest request);

    @Operation(
            summary = "Sign in",
            description = """
                    Authenticates with email + password. Returns a signed JWT access token in the response body \
                    and sets an HttpOnly refresh-token cookie named {@code rt}.""")
    @ApiResponse(
            responseCode = "200",
            description = "Authenticated — access token in body, refresh token in HttpOnly cookie",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @PostMapping(path = "/login", version = ApiVersioning.V1)
    ResponseEntity<AuthResponse> signIn(@Valid @RequestBody LoginRequest request,
                                        HttpServletResponse response);

    @Operation(
            summary = "Refresh the access token",
            description = """
                    Reads the {@code rt} HttpOnly cookie, validates the refresh token, rotates it, and returns \
                    a new access token in the body plus a new refresh-token cookie.""")
    @ApiResponse(
            responseCode = "200",
            description = "Token refreshed — new access token in body, new refresh-token cookie set",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @PostMapping(path = "/refresh", version = ApiVersioning.V1)
    ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "rt", required = false) String rawRefreshToken,
            HttpServletResponse response);

    @Operation(
            summary = "Sign out",
            description = "Revokes the refresh token and clears the {@code rt} cookie. Idempotent.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    @ApiStandardErrorResponses
    @PostMapping(path = "/logout", version = ApiVersioning.V1)
    ResponseEntity<Void> signOut(
            @CookieValue(name = "rt", required = false) String rawRefreshToken,
            HttpServletResponse response);

    @Operation(summary = "Verify email address", description = "Validates the one-time token sent to the user's email and activates the account.")
    @ApiResponse(responseCode = "204", description = "Email verified — account is now active")
    @ApiResponseBadRequest
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @PostMapping(path = "/verify-email", version = ApiVersioning.V1)
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request);

    @Operation(
            summary = "Forgot password",
            description = """
                    Sends a password-reset link to the given email address if an active account exists.
                    Always returns 204 regardless of whether the email is registered, to prevent account
                    enumeration.""")
    @ApiResponse(responseCode = "204", description = "Reset link sent (or silently ignored)")
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @PostMapping(path = "/forgot-password", version = ApiVersioning.V1)
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request);

    @Operation(
            summary = "Reset password",
            description = "Applies a new password using the one-time token delivered by the forgot-password flow.")
    @ApiResponse(responseCode = "204", description = "Password reset")
    @ApiResponseBadRequest
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @PostMapping(path = "/reset-password", version = ApiVersioning.V1)
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request);

    @Operation(
            summary = "Resend verification email",
            description = """
                    Resends the email-verification link for an account still in {@code PENDING_VERIFICATION}.
                    Always returns 204 regardless of whether the email is registered, to prevent account
                    enumeration.""")
    @ApiResponse(responseCode = "204", description = "Verification email resent (or silently ignored)")
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @PostMapping(path = "/resend-verification", version = ApiVersioning.V1)
    ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request);
}
