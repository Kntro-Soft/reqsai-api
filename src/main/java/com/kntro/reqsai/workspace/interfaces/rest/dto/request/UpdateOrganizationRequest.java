package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update (PATCH) body for an organization's editable settings. Every field is optional:
 * only fields present (non-null) are applied; omitted fields are left unchanged. When a field IS
 * provided, its usual constraints still apply.
 */
@Schema(description = "Partial update body for an organization's editable settings. Omit a field to leave it unchanged.")
public record UpdateOrganizationRequest(

        @Schema(
                description = "Organization display name. Omit to leave unchanged.",
                example = "Acme Corp International",
                maxLength = 150,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(min = 1, max = 150)
        String name,

        @Schema(
                description = "Default BCP-47 language for meetings in this organization. Omit to leave unchanged.",
                example = "es-PE",
                maxLength = 8,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(min = 1, max = 8)
        String meetingLanguage,

        @Schema(
                description = "Number of days audio recordings are retained before deletion. Use -1 to keep forever, "
                        + "0 to delete immediately. Omit to leave unchanged.",
                example = "30",
                minimum = "-1",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Min(-1)
        Integer audioRetentionDays
) {}
