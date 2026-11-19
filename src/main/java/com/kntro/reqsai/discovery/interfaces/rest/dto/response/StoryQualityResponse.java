package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Latest quality assessment of a user story (ISO/IEC/IEEE 29148 and Quality User Story characteristics)")
public record StoryQualityResponse(

        @Schema(description = "Story the assessment is about", example = "019756a0-1234-7abc-8def-000000000010")
        UUID storyId,

        @Schema(description = "Overall quality score, from 0 to 100", example = "81", minimum = "0", maximum = "100")
        int score,

        @Schema(description = "One finding per evaluated characteristic: ambiguity, atomicity, verifiability and completeness")
        List<QualityFindingResponse> characteristics,

        @Schema(description = "When the story was evaluated", example = "2026-11-18T12:00:00Z")
        Instant evaluatedAt
) {
}
