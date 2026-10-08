package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.SessionSpeakerUpdatedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionSpeakerUpdatedMessage;

/** Maps {@link SessionSpeakerUpdatedEvent} to its {@link SessionEventType#SPEAKER_UPDATED} WebSocket payload. */
public final class SpeakerNotificationMapper {

    private SpeakerNotificationMapper() {
    }

    public static SessionSpeakerUpdatedMessage toMessage(SessionSpeakerUpdatedEvent event) {
        return new SessionSpeakerUpdatedMessage(
                event.sessionId(),
                event.speakerLabel(),
                event.displayName(),
                event.side() != null ? event.side().name() : null,
                event.occurredAt());
    }
}
