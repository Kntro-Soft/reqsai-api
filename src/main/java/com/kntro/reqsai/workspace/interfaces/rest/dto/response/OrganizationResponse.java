package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Organization resource")
public record OrganizationResponse(

        @Schema(description = "Organization unique identifier", example = "019756a0-1234-7abc-8def-000000000001")
        UUID id,

        @Schema(description = "Organization display name", example = "Acme Corp")
        String name,

        @Schema(description = "URL-safe slug used in the tenant schema name", example = "acme-corp")
        String slug,

        @Schema(
                description = "Current status of the organization",
                example = "ACTIVE",
                allowableValues = {"PENDING", "ACTIVE", "SUSPENDED"})
        String status,

        @Schema(description = "User ID of the organization owner", example = "019756a0-1234-7abc-8def-000000000099")
        UUID ownerId,

        @Schema(description = "Default BCP-47 language for meetings in this organization", example = "es-PE")
        String meetingLanguage,

        @Schema(description = "Number of days audio recordings are retained before deletion", example = "30")
        int audioRetentionDays,

        @Schema(description = "Timestamp when the organization was created", example = "2026-06-15T13:55:00Z")
        Instant createdAt,

        @Schema(description = "Timestamp of the last update", example = "2026-06-15T13:55:00Z")
        Instant updatedAt
) {
}
