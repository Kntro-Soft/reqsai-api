package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingCompletedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingFailedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingPausedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingResumedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionTranscriptUploadedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionProcessingFailedMessage;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionStatusChangedMessage;

/**
 * Maps {@link com.kntro.reqsai.discovery.domain.model.DiscoverySession} domain events to their
 * corresponding WebSocket payload messages.
 *
 */
public final class DiscoverySessionNotificationMapper {

    private DiscoverySessionNotificationMapper() {
    }

    /** {@link SessionEventType#RECORDING_STARTED} — {@code DRAFT → RECORDING}. */
    public static SessionStatusChangedMessage toMessage(DiscoverySessionRecordingStartedEvent event) {
        return SessionStatusChangedMessage.of(event.sessionId(), SessionEventType.RECORDING_STARTED, event.occurredAt());
    }

    /** {@link SessionEventType#RECORDING_PAUSED} — {@code RECORDING → PAUSED}. */
    public static SessionStatusChangedMessage toMessage(DiscoverySessionRecordingPausedEvent event) {
        return SessionStatusChangedMessage.of(event.sessionId(), SessionEventType.RECORDING_PAUSED, event.occurredAt());
    }

    /** {@link SessionEventType#RECORDING_RESUMED} — {@code PAUSED → RECORDING}. */
    public static SessionStatusChangedMessage toMessage(DiscoverySessionRecordingResumedEvent event) {
        return SessionStatusChangedMessage.of(event.sessionId(), SessionEventType.RECORDING_RESUMED, event.occurredAt());
    }

    /** {@link SessionEventType#RECORDING_STOPPED} — {@code RECORDING/PAUSED → STOPPED}. */
    public static SessionStatusChangedMessage toMessage(DiscoverySessionRecordingStoppedEvent event) {
        return SessionStatusChangedMessage.of(event.sessionId(), SessionEventType.RECORDING_STOPPED, event.occurredAt());
    }

    /** {@link SessionEventType#TRANSCRIPT_UPLOADED} — transcript attached via file upload ({@code DRAFT → STOPPED}). */
    public static SessionStatusChangedMessage toMessage(DiscoverySessionTranscriptUploadedEvent event) {
        return SessionStatusChangedMessage.of(event.sessionId(), SessionEventType.TRANSCRIPT_UPLOADED, event.occurredAt());
    }

    /** {@link SessionEventType#PROCESSING} — AI extraction started ({@code STOPPED/FAILED → PROCESSING}). */
    public static SessionStatusChangedMessage toMessage(DiscoverySessionProcessingStartedEvent event) {
        return SessionStatusChangedMessage.of(event.sessionId(), SessionEventType.PROCESSING, event.occurredAt());
    }

    /** {@link SessionEventType#COMPLETED} — AI extraction finished ({@code PROCESSING → COMPLETED}). */
    public static SessionStatusChangedMessage toMessage(DiscoverySessionProcessingCompletedEvent event) {
        return SessionStatusChangedMessage.of(event.sessionId(), SessionEventType.COMPLETED, event.occurredAt());
    }

    /** {@link SessionEventType#FAILED} — AI extraction failed; {@code reason} is forwarded to the client. */
    public static SessionProcessingFailedMessage toMessage(DiscoverySessionProcessingFailedEvent event) {
        return new SessionProcessingFailedMessage(event.sessionId(), event.reason(), event.occurredAt());
    }
}
