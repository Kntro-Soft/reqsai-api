package com.kntro.reqsai.iam.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Authenticated user profile")
public record UserResponse(

        @Schema(description = "User unique identifier (the JWT subject)", example = "019756a0-1234-7abc-8def-000000000099")
        UUID id,

        @Schema(description = "User's first name", example = "Jane")
        String firstName,

        @Schema(description = "User's last name", example = "Doe")
        String lastName,

        @Schema(description = "User's full name", example = "Jane Doe")
        String fullName,

        @Schema(description = "Path to the user's avatar image served by this API",
                example = "/api/users/019756a0-1234-7abc-8def-000000000099/avatar")
        String avatarUrl,

        @Schema(description = "User's navigation preferences")
        UserPreferencesResponse preferences
) {
}
