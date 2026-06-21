package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/auth/accept-terms}.
 *
 * @param termsVersion the version string of the T&C being accepted (e.g. {@code "2026-01"})
 */
public record AcceptTermsRequest(
        @NotBlank(message = "termsVersion must not be blank")
        @Size(max = 50, message = "termsVersion must be at most 50 characters")
        String termsVersion
) {
}
