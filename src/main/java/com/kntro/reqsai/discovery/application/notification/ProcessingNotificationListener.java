package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingCompletedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingFailedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionTranscriptUploadedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.mappers.SessionNotificationMapper;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionRealtimeMessage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bridges AI processing lifecycle domain events to live WebSocket notifications.
 *
 * <p>Covers the four processing state transitions: transcript uploaded, processing started,
 * processing completed, and processing failed. Failed events additionally log the failure reason
 * at debug level so operators can trace extraction errors without opening the payload.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ProcessingNotificationListener {

    private final RealtimeNotifier notifier;

    @ApplicationModuleListener
    void onTranscriptUploaded(DiscoverySessionTranscriptUploadedEvent event) {
        broadcast(event.sessionId(), SessionNotificationMapper.toMessage(event));
    }

    @ApplicationModuleListener
    void onProcessingStarted(DiscoverySessionProcessingStartedEvent event) {
        broadcast(event.sessionId(), SessionNotificationMapper.toMessage(event));
    }

    @ApplicationModuleListener
    void onProcessingCompleted(DiscoverySessionProcessingCompletedEvent event) {
        broadcast(event.sessionId(), SessionNotificationMapper.toMessage(event));
    }

    @ApplicationModuleListener
    void onProcessingFailed(DiscoverySessionProcessingFailedEvent event) {
        var message = SessionNotificationMapper.toMessage(event);
        log.debug("Notifying {} for session {}: {}", message.type(), event.sessionId(), event.reason());
        notifier.broadcast(SessionTopics.of(event.sessionId()), message);
    }

    private void broadcast(UUID sessionId, SessionRealtimeMessage message) {
        log.debug("Notifying {} for session {}", message.type(), sessionId);
        notifier.broadcast(SessionTopics.of(sessionId), message);
    }
}
