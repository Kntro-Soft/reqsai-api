package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.discovery.domain.model.SpeakerSide;
import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when the analyst names a diarized speaker of a session or says which side they are on, so every
 * viewer of the session relabels that speaker's segments.
 *
 * @param sessionId    session the speaker belongs to
 * @param speakerLabel provider diarization label (e.g. {@code "0"}, {@code "A"})
 * @param displayName  the name the analyst gave, or {@code null} for the default "Hablante N"
 * @param side         client or team, or {@code null} when not set
 */
public record SessionSpeakerUpdatedEvent(
        UUID sessionId,
        String speakerLabel,
        @Nullable String displayName,
        @Nullable SpeakerSide side,
        Instant occurredAt
) implements DomainEvent {

    public static SessionSpeakerUpdatedEvent of(UUID sessionId, String speakerLabel, @Nullable String displayName,
                                                @Nullable SpeakerSide side) {
        return new SessionSpeakerUpdatedEvent(sessionId, speakerLabel, displayName, side, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
