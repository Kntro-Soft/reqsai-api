package com.kntro.reqsai.iam.interfaces.rest.swagger;

import com.kntro.reqsai.iam.interfaces.rest.dto.request.AcceptTermsRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.ChangePasswordRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.UpdatePreferencesRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.UpdateProfileRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseUnauthorized;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * API contract (OpenAPI documentation) for user self-service endpoints.
 * All endpoints require a valid JWT — the security filter returns 401 before validation runs.
 * Implementation: {@code controllers.UserControllerImpl}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User Profile", description = "Authenticated user profile and account management")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
public interface UserController {

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
    @PostMapping(path = "/me/terms", version = ApiVersioning.V1)
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
            description = "Preferences updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @PatchMapping(path = "/me/preferences", version = ApiVersioning.V1)
    ResponseEntity<UserResponse> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request,
            Authentication authentication);

    @Operation(
            summary = "Update profile",
            description = "Updates the authenticated user's first name, last name, and avatar URL.")
    @ApiResponse(
            responseCode = "200",
            description = "Profile updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @PatchMapping(path = "/me", version = ApiVersioning.V1)
    ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication);

    @Operation(
            summary = "Change password",
            description = "Changes the authenticated user's password after verifying the current one.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponseBadRequest
    @ApiResponseUnauthorized
    @ApiStandardErrorResponses
    @PutMapping(path = "/me/password", version = ApiVersioning.V1)
    ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication);
}
