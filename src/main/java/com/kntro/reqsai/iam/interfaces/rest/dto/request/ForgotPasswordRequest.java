package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/auth/forgot-password}.
 */
@Schema(description = "Request body to initiate the password-reset flow")
public record ForgotPasswordRequest(

        @Schema(description = "Email address of the account requesting a password reset",
                example = "jane.doe@acme.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320)
        String email
) {}
