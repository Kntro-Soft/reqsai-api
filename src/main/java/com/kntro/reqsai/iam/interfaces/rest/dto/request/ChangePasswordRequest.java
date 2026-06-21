package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/auth/me/password}.
 */
@Schema(description = "Request body to change the authenticated user's password")
public record ChangePasswordRequest(

        @Schema(description = "The user's current password", example = "OldPass123!",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "currentPassword must not be blank")
        String currentPassword,

        @Schema(description = "The desired new password (8–72 characters)", example = "NewPass456!",
                minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 8, max = 72)
        String newPassword
) {}
