package com.kntro.reqsai.iam.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Schema(description = "Authentication result: access token and the authenticated user")
public record AuthResponse(

        @Schema(description = "Signed JWT access token", example = "eyJhbGciOiJSUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Token type for the Authorization header", example = "Bearer")
        String tokenType,

        @Schema(description = "Seconds until the access token expires", example = "900")
        long expiresIn,

        @Schema(description = "The authenticated user")
        UserResponse user,

        @Schema(description = "The organization owned by the user, null if none exists yet", nullable = true)
        @Nullable UUID organizationId
) {
}
