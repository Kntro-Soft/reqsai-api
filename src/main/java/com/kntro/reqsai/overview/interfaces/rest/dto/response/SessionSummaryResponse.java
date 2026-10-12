package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A recently created discovery session")
public record SessionSummaryResponse(UUID id, UUID projectId, String title,
        @Schema(example = "COMPLETED") String status, Instant createdAt) {
}
