package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingPausedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingResumedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.mappers.DiscoverySessionNotificationMapper;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionRealtimeMessage;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionStatusChangedMessage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bridges recording lifecycle domain events to live WebSocket notifications.
 *
 * <p>Covers the four recording state transitions: start, pause, resume, and stop.
 * Each handler maps the event to a {@link SessionStatusChangedMessage}
 * and broadcasts it on the session topic via {@link SessionTopics}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class RecordingNotificationListener {

    private final RealtimeNotifier notifier;

    @ApplicationModuleListener
    void onRecordingStarted(DiscoverySessionRecordingStartedEvent event) {
        broadcast(event.sessionId(), DiscoverySessionNotificationMapper.toMessage(event));
    }

    @ApplicationModuleListener
    void onRecordingPaused(DiscoverySessionRecordingPausedEvent event) {
        broadcast(event.sessionId(), DiscoverySessionNotificationMapper.toMessage(event));
    }

    @ApplicationModuleListener
    void onRecordingResumed(DiscoverySessionRecordingResumedEvent event) {
        broadcast(event.sessionId(), DiscoverySessionNotificationMapper.toMessage(event));
    }

    @ApplicationModuleListener
    void onRecordingStopped(DiscoverySessionRecordingStoppedEvent event) {
        broadcast(event.sessionId(), DiscoverySessionNotificationMapper.toMessage(event));
    }

    private void broadcast(UUID sessionId, SessionRealtimeMessage message) {
        log.debug("Notifying {} for session {}", message.type(), sessionId);
        notifier.broadcast(SessionTopics.of(sessionId), message);
    }
}
