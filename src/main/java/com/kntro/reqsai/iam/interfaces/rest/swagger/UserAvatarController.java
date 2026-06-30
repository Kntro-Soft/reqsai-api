package com.kntro.reqsai.iam.interfaces.rest.swagger;

import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * API contract (OpenAPI documentation) for the user avatar endpoints. The implementation lives in
 * {@code controllers.UserAvatarControllerImpl}.
 * <p>
 * The GET is public on purpose: the bytes are loaded by browser {@code <img src>} tags, which cannot
 * carry the Bearer token. The image is a non-sensitive generated identicon and the user id is an
 * unguessable UUIDv7. Whitelisted in {@code SecurityConfiguration}. The PUT upload is authenticated — the
 * caller may only replace their own avatar.
 */
@RequestMapping(path = ApiVersioning.BASE + "/users")
@Tag(name = "User Profile", description = "Authenticated user profile and account management")
public interface UserAvatarController {

    @Operation(
            summary = "Get a user's avatar",
            description = "Returns the user's generated avatar image bytes. Public — no authentication required.")
    @ApiResponse(
            responseCode = "200",
            description = "Avatar image",
            content = @Content(schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "User has no avatar", content = @Content)
    @GetMapping(path = "/{userId}/avatar", version = ApiVersioning.V1)
    ResponseEntity<byte[]> getAvatar(@Parameter(description = "User UUID") @PathVariable UUID userId);

    @Operation(
            summary = "Upload the current user's avatar",
            description = """
                    Replaces the authenticated user's avatar with an uploaded image (svg, png, jpeg or webp,
                    max 1 MB). Returns the updated user profile.""")
    @ApiResponse(
            responseCode = "200",
            description = "Avatar updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing, oversized or non-image file", content = @Content)
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(path = "/me/avatar", version = ApiVersioning.V1, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UserResponse> uploadAvatar(
            @Parameter(description = "Image file (svg, png, jpeg, webp; max 1 MB)", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication);
}
