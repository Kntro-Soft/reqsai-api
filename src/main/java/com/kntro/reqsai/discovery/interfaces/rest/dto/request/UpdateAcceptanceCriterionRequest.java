package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request body to update an acceptance criterion")
public record UpdateAcceptanceCriterionRequest(

        @Schema(description = "Optional scenario label. Pass null to clear it.",
                maxLength = 200, requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        @Size(max = 200)
        @Nullable String scenario,

        @Schema(description = "Given — precondition or context. Max 1000 chars.",
                example = "the user is logged in", minLength = 1, maxLength = 1000,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1000)
        String given,

        @Schema(description = "When — action or trigger. Max 1000 chars.",
                example = "the user uploads a CSV file", minLength = 1, maxLength = 1000,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1000)
        String when,

        @Schema(description = "Then — expected outcome. Max 1000 chars.",
                example = "the system imports all rows and shows a success summary", minLength = 1, maxLength = 1000,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1000)
        String then
) {}
