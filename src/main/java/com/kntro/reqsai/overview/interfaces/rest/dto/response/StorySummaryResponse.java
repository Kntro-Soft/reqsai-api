package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A recently created user story")
public record StorySummaryResponse(UUID id, UUID projectId, String title, @Schema(example = "DRAFT") String status,
        @Schema(example = "HIGH") String priority, Instant createdAt) {
}
