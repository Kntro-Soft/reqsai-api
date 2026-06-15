package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Discovery session resource")
public record DiscoverySessionResponse(

        @Schema(description = "Session unique identifier", example = "019756a0-1234-7abc-8def-000000000001")
        UUID id,

        @Schema(description = "Project this session belongs to", example = "019756a0-1234-7abc-8def-000000000002")
        UUID projectId,

        @Schema(description = "Descriptive title for the session", example = "Sprint 24 — Requirements Elicitation")
        String title,

        @Schema(description = "BCP-47 language tag used by STT and AI generation", example = "es-PE")
        String language,

        @Schema(
                description = "Current lifecycle state of the session",
                example = "DRAFT",
                allowableValues = {"DRAFT", "RECORDING", "PAUSED", "STOPPED", "PROCESSING", "COMPLETED", "FAILED"})
        String status,

        @Schema(description = "Timestamp when recording started; null until startRecording() is called", example = "2026-06-15T14:00:00Z", nullable = true)
        Instant startedAt,

        @Schema(description = "Timestamp when recording stopped; null until stopRecording() is called", example = "2026-06-15T15:30:00Z", nullable = true)
        Instant endedAt,

        @Schema(description = "Cumulative audio duration in milliseconds across all appended segments", example = "5400000")
        long audioDurationMs,

        @Schema(description = "Error message when status is FAILED; null otherwise", example = "AI provider quota exceeded", nullable = true)
        String processingError,

        @Schema(description = "Timestamp when the session was created", example = "2026-06-15T13:55:00Z")
        Instant createdAt,

        @Schema(description = "Timestamp of the last update", example = "2026-06-15T15:30:05Z")
        Instant updatedAt
) {
}
