package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body to verify an email address using the one-time token")
public record VerifyEmailRequest(

        @Schema(description = "Raw one-time token from the verification link",
                example = "a3f9c2d1e8b74056a1c3e2f9d8b74056",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "token must not be blank")
        String token
) {}
