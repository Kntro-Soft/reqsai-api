package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import com.kntro.reqsai.workspace.interfaces.rest.dto.request.TechnicalProfileDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Created project details")
public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        TechnicalProfileDto technicalProfile,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
