package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to register a new account and user profile")
public record RegisterRequest(

        @Schema(description = "Account email (unique, case-insensitive)", example = "jane.doe@acme.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320)
        String email,

        @Schema(description = "Password (8–72 characters)", example = "S3curePass!",
                minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Schema(description = "User's first name", example = "Jane",
                maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(description = "User's last name", example = "Doe",
                maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100)
        String lastName
) {
}
