package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for {@link SessionEventType#SPEAKER_UPDATED}: the analyst named a diarized speaker or
 * set their side, so every viewer relabels that speaker's segments.
 *
 * @param sessionId    session the speaker belongs to
 * @param speakerLabel provider diarization label of the speaker
 * @param displayName  the new name, or {@code null} for the default "Hablante N"
 * @param side         {@code CLIENT}, {@code TEAM}, or {@code null} when not set
 * @param occurredAt   when the description changed
 */
public record SessionSpeakerUpdatedMessage(
        UUID sessionId,
        String speakerLabel,
        @Nullable String displayName,
        @Nullable String side,
        Instant occurredAt
) implements SessionRealtimeMessage {

    @Override
    @JsonProperty("type")
    public SessionEventType type() {
        return SessionEventType.SPEAKER_UPDATED;
    }
}
