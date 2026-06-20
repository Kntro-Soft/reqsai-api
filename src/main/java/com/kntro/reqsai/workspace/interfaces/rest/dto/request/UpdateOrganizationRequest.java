package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to update an organization's editable settings")
public record UpdateOrganizationRequest(

        @Schema(
                description = "Organization display name",
                example = "Acme Corp International",
                minLength = 1,
                maxLength = 150,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 150)
        String name,

        @Schema(
                description = "Default BCP-47 language for meetings in this organization",
                example = "es-PE",
                maxLength = 8,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 8)
        String meetingLanguage,

        @Schema(
                description = "Number of days audio recordings are retained before deletion. Use -1 to keep forever, 0 to delete immediately",
                example = "30",
                minimum = "-1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(-1)
        int audioRetentionDays
) {}
