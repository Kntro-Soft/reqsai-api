package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One round of the assistant chat: the analyst's message and ReqsAI's reply")
public record AssistantExchangeResponse(

        @Schema(description = "What the analyst typed, as stored")
        AssistantMessageResponse question,

        @Schema(description = "ReqsAI's reply, with any suggestions it raised")
        AssistantMessageResponse answer
) {
}
