package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/auth/reset-password}.
 */
@Schema(description = "Request body to apply a password reset using the one-time token")
public record ResetPasswordRequest(

        @Schema(description = "Raw one-time reset token from the password-reset link",
                example = "a3f9c2d1e8b74056a1c3e2f9d8b74056a3f9c2d1e8b74056a1c3e2f9d8b7405",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "token must not be blank")
        String token,

        @Schema(description = "The desired new password (8–72 characters)", example = "NewPass456!",
                minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 8, max = 72)
        String newPassword
) {}
