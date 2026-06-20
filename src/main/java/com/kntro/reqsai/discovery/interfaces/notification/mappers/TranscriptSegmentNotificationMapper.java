package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.TranscriptSegmentAppendedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionTranscriptSegmentMessage;

/**
 * Maps {@link TranscriptSegmentAppendedEvent} (raised during live-streaming capture) to its
 * {@link SessionEventType#TRANSCRIPT_SEGMENT} WebSocket payload.
 */
public final class TranscriptSegmentNotificationMapper {

    private TranscriptSegmentNotificationMapper() {
    }

    public static SessionTranscriptSegmentMessage toMessage(TranscriptSegmentAppendedEvent event) {
        return new SessionTranscriptSegmentMessage(
                event.sessionId(),
                event.sequence(),
                event.speakerLabel(),
                event.text(),
                event.startMs(),
                event.endMs(),
                event.isFinal(),
                event.occurredAt()
        );
    }
}
