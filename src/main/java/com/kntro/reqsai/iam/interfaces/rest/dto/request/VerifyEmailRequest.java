package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank
        String token
) {}
