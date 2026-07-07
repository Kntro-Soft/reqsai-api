package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Session transcript text")
public record TranscriptResponse(

        @Schema(description = "Session unique identifier", example = "019756a0-1234-7abc-8def-000000000001")
        UUID sessionId,

        @Schema(description = "Full transcript text; null if not yet transcribed", nullable = true)
        String transcript
) {}
