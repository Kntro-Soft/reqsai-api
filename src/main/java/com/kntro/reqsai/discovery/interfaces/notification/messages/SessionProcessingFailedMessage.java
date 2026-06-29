package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for {@link SessionEventType#FAILED} — AI extraction failed.
 * Separated from {@link SessionStatusChangedMessage} because it carries a non-nullable
 * {@code reason} field that the client surfaces to the user.
 */
public record SessionProcessingFailedMessage(
        UUID sessionId,
        String reason,
        Instant occurredAt
) implements SessionRealtimeMessage {

    @Override
    @JsonProperty("type")
    public SessionEventType type() {
        return SessionEventType.FAILED;
    }
}
