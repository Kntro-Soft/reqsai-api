package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionCodeResponse;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for {@link SessionEventType#SUGGESTION_CODE_UPDATED}: what the code says about a suggestion
 * changed because its deep verification started, finished or failed. {@code sessionId} is null for a suggestion
 * raised from the assistant chat, which is announced on the project's suggestions topic only.
 */
public record SessionSuggestionCodeMessage(
        @Nullable UUID sessionId,
        UUID projectId,
        UUID suggestionId,
        SessionEventType type,
        @Nullable SuggestionCodeResponse code,
        Instant occurredAt
) implements SessionRealtimeMessage {

    @Override
    public SessionEventType type() {
        return type;
    }
}
