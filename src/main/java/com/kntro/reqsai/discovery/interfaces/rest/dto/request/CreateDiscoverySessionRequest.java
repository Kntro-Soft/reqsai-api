package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to create a requirements-elicitation session")
public record CreateDiscoverySessionRequest(

        @Schema(
                description = "Descriptive title for the session",
                example = "Sprint 24 — Requirements Elicitation",
                minLength = 1,
                maxLength = 200,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String title,

        @Schema(
                description = "BCP-47 language tag for the meeting — used by STT and AI generation",
                example = "es-PE",
                minLength = 2,
                maxLength = 8,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 8)
        String language
) {
}
