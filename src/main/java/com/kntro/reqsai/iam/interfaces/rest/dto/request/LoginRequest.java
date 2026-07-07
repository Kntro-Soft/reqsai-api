package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body to authenticate with email and password")
public record LoginRequest(

        @Schema(description = "Account email", example = "jane.doe@acme.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String email,

        @Schema(description = "Account password", example = "S3curePass!",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String password
) {
}
