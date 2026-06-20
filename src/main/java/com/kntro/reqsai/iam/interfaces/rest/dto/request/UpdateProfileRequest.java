package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Request body for {@code PATCH /api/auth/me}.
 */
@Schema(description = "Request body to update the authenticated user's profile")
public record UpdateProfileRequest(

        @Schema(description = "User's first name", example = "Jane",
                maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(description = "User's last name", example = "Doe",
                maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100)
        String lastName,

        @Schema(description = "URL of the user's avatar image (null to clear)",
                example = "https://cdn.example.com/avatars/jane.png", maxLength = 2048,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Nullable @Size(max = 2048)
        String avatarUrl
) {}
