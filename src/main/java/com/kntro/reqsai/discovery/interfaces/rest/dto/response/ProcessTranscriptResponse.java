package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Result of AI extraction over the session transcript")
public record ProcessTranscriptResponse(

        @Schema(description = "Session resource after processing (status COMPLETED or FAILED)")
        DiscoverySessionResponse session,

        @Schema(description = "User stories extracted by the AI model; empty when session ended in FAILED")
        List<UserStoryResponse> stories
) {}
