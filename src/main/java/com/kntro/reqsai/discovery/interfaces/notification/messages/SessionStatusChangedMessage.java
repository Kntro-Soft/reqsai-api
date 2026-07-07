package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for all simple session lifecycle transitions — recording and AI processing
 * state changes that carry no extra data beyond the new status.
 * <p>
 * Covers: {@link SessionEventType#RECORDING_STARTED}, {@link SessionEventType#RECORDING_PAUSED},
 * {@link SessionEventType#RECORDING_RESUMED}, {@link SessionEventType#RECORDING_STOPPED},
 * {@link SessionEventType#TRANSCRIPT_UPLOADED},
 * {@link SessionEventType#PROCESSING}, {@link SessionEventType#COMPLETED}.
 * <p>
 */
public record SessionStatusChangedMessage(
        UUID sessionId,
        SessionEventType type,
        Instant occurredAt
) implements SessionRealtimeMessage {

    public static SessionStatusChangedMessage of(UUID sessionId, SessionEventType type, Instant occurredAt) {
        return new SessionStatusChangedMessage(sessionId, type, occurredAt);
    }
}
