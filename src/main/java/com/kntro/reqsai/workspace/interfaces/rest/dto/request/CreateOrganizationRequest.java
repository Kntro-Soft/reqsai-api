package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Request body for {@code POST /api/v1/organizations}. Only {@code name} is required; {@code slug} is
 * derived from the name and {@code meetingLanguage} defaults when omitted.
 */
public record CreateOrganizationRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @Nullable
        @Size(max = 50)
        String slug,

        @Nullable
        @Size(max = 8)
        String meetingLanguage
) {
}
