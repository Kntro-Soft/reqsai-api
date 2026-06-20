package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Project constraint resource")
public record ProjectConstraintResponse(
        @Schema(description = "Unique identifier of the constraint") UUID id,
        @Schema(description = "Constraint description", example = "Must comply with PCI-DSS") String description,
        @Schema(description = "Timestamp when the constraint was created") Instant createdAt,
        @Schema(description = "Timestamp of the last update") Instant updatedAt
) {}
