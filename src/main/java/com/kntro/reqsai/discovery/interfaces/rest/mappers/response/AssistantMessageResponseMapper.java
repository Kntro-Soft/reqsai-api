package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.application.handler.AssistantChatEntry;
import com.kntro.reqsai.discovery.application.handler.AssistantExchange;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AssistantExchangeResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AssistantMessageResponse;

/** Maps assistant chat entries to their REST representation. */
public final class AssistantMessageResponseMapper {

    private AssistantMessageResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static AssistantMessageResponse toResponse(AssistantChatEntry entry) {
        return new AssistantMessageResponse(
                entry.message().getId(),
                entry.message().getRole().name(),
                entry.message().getContent(),
                entry.message().getCreatedAt(),
                entry.suggestions().stream().map(SuggestionResponseMapper::toResponse).toList());
    }

    public static AssistantExchangeResponse toResponse(AssistantExchange exchange) {
        return new AssistantExchangeResponse(toResponse(exchange.question()), toResponse(exchange.answer()));
    }
}
