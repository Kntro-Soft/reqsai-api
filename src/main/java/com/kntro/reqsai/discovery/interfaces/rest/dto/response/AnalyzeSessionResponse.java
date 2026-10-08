package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of an on-demand analysis of a live session")
public record AnalyzeSessionResponse(

        @Schema(description = "Suggestions raised; they reach the review tray through the session's realtime topic. "
                + "0 when there was no new conversation to analyze.", example = "2")
        int suggestionsCreated
) {
}
