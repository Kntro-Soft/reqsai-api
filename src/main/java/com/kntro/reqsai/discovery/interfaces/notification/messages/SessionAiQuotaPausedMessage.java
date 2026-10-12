package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for {@link SessionEventType#AI_QUOTA_PAUSED}: the organization's monthly AI allowance
 * is spent and live suggestions pause for this session. The figures are {@code null} when the allowance
 * could not be read back.
 */
public record SessionAiQuotaPausedMessage(
        UUID sessionId,
        @Nullable String plan,
        @Nullable Long tokensLimit,
        @Nullable Long tokensUsed,
        @Nullable String resetsAt,
        Instant occurredAt
) implements SessionRealtimeMessage {

    @Override
    @JsonProperty("type")
    public SessionEventType type() {
        return SessionEventType.AI_QUOTA_PAUSED;
    }
}
