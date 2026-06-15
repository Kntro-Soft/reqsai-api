package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "User story resource")
public record UserStoryResponse(

        @Schema(description = "Story unique identifier", example = "019756a0-1234-7abc-8def-000000000010")
        UUID id,

        @Schema(description = "Project this story belongs to", example = "019756a0-1234-7abc-8def-000000000002")
        UUID projectId,

        @Schema(description = "Originating discovery session; null for manually-created stories", nullable = true)
        @Nullable UUID sessionId,

        @Schema(description = "Short story title", example = "Bulk-import suppliers")
        String title,

        @Schema(description = "Actor", example = "compliance analyst")
        String role,

        @Schema(description = "Desired action", example = "upload a CSV of suppliers")
        String action,

        @Schema(description = "Expected benefit", example = "I avoid entering them one by one")
        String benefit,

        @Schema(description = "Backlog priority", example = "HIGH",
                allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
        String priority,

        @Schema(description = "Effort estimate in story points; null if not estimated", example = "5", nullable = true)
        @Nullable Integer storyPoints,

        @Schema(description = "Review status", example = "DRAFT",
                allowableValues = {"DRAFT", "APPROVED", "REJECTED", "MERGED", "EXPORTED"})
        String status,

        @Schema(description = "Timestamp when the story was created", example = "2026-06-15T13:55:00Z")
        Instant createdAt,

        @Schema(description = "Timestamp of the last update", example = "2026-06-15T13:55:00Z")
        Instant updatedAt
) {
}
