package com.kntro.reqsai.iam.interfaces.rest.swagger;

import com.kntro.reqsai.iam.interfaces.rest.dto.request.AcceptTermsRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.LoginRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.RegisterRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.UpdatePreferencesRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.VerifyEmailRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.AuthResponse;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseConflict;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseUnauthorized;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * API contract (OpenAPI documentation) for authentication endpoints. The implementation lives in
 * {@code controllers.AuthControllerImpl}; keeping the annotations here leaves the controller free of
 * documentation noise.
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
            summary = "Get the current user",
            description = "Returns the profile of the authenticated user (the JWT subject).")
    @ApiResponse(
            responseCode = "200",
            description = "Current user profile",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/me", version = ApiVersioning.V1)
    ResponseEntity<UserResponse> me(Authentication authentication);

    @Operation(
            summary = "Accept Terms and Conditions",
            description = """
                    Records the user's acceptance of the specified T&C version. After this call, request a
                    token refresh ({@code POST /api/auth/refresh}) — the new JWT will carry the
                    {@code termsVersion} claim, which the frontend uses to unlock the onboarding flow.""")
    @ApiResponse(responseCode = "204", description = "Terms accepted")
    @ApiResponseBadRequest
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/accept-terms", version = ApiVersioning.V1)
    ResponseEntity<Void> acceptTerms(
            @Valid @RequestBody AcceptTermsRequest request,
            Authentication authentication);

    @Operation(
            summary = "Update navigation preferences",
            description = """
                    Persists the active organization context for the authenticated user. After this call,
                    the next `POST /api/auth/refresh` will embed the selected {@code orgId} in the new JWT,
                    effectively switching the active organization. Send {@code lastVisitedOrgId: null} to clear
                    the preference and fall back to the most-recently created organization.""")
    @ApiResponse(
            responseCode = "200",
            description = "Preferences updated — updated user profile returned",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PatchMapping(path = "/me/preferences", version = ApiVersioning.V1)
    ResponseEntity<UserResponse> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request,
            Authentication authentication);
}
