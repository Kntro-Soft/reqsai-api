package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/auth/resend-verification}.
 */
@Schema(description = "Request body to resend the email-verification link")
public record ResendVerificationRequest(

        @Schema(description = "Email address of the account that needs re-verification",
                example = "jane.doe@acme.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320)
        String email
) {}
