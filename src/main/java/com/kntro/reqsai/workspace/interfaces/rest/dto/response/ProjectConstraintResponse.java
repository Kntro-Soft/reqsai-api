package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Project constraint resource")
public record ProjectConstraintResponse(

        @Schema(description = "Project constraint unique identifier", example = "019756a0-1234-7abc-8def-000000000401")
        UUID id,

        @Schema(description = "Constraint or condition recorded for the project", example = "Debe integrarse con SAP")
        String description,

        @Schema(description = "Timestamp when the resource was created", example = "2026-06-20T13:30:00Z")
        Instant createdAt,

        @Schema(description = "Timestamp of the last update", example = "2026-06-20T13:30:00Z")
        Instant updatedAt
) {}
