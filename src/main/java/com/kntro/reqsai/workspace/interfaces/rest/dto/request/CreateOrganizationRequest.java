package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request body to create an organization and provision its tenant schema")
public record CreateOrganizationRequest(

        @Schema(
                description = "Organization display name",
                example = "Acme Corp",
                minLength = 1,
                maxLength = 150,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 150)
        String name,

        @Schema(
                description = "URL-safe slug used in the tenant schema name (auto-derived from name if omitted)",
                example = "acme-corp",
                maxLength = 50,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Nullable
        @Size(max = 50)
        String slug,

        @Schema(
                description = "Default BCP-47 language for meetings in this organization (defaults to es-PE if omitted)",
                example = "es-PE",
                maxLength = 8,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Nullable
        @Size(max = 8)
        String meetingLanguage
) {
}
