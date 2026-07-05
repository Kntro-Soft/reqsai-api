package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionCreatedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingPausedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingResumedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.mappers.DiscoverySessionNotificationMapper;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionLifecycleMessage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Bridges session lifecycle domain events to the <strong>project-level</strong> realtime topic
 * ({@link ProjectTopics#sessionsOf}).
 *
 * <p>Complements {@link RecordingNotificationListener} (which serves viewers already subscribed to
 * one session's topic): a viewer on the project's discovery page has no session subscription yet,
 * so without this broadcast it could never learn that someone else created or started a session.
 * The {@link SessionLifecycleMessage} payload is self-describing (status, title, language,
 * startedAt) so the project page can render the session row — including the meeting language —
 * without an extra fetch.
 *
 * <p>Topic subscription follows the same auth model as the per-session topics: the STOMP CONNECT
 * frame is JWT-authenticated by {@code StompAuthChannelInterceptor}; no per-destination gate exists
 * for session topics, and none is added here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SessionLifecycleNotificationListener {

    private final RealtimeNotifier notifier;

    @ApplicationModuleListener
    void onSessionCreated(DiscoverySessionCreatedEvent event) {
        broadcast(DiscoverySessionNotificationMapper.toProjectMessage(event));
    }

    @ApplicationModuleListener
    void onRecordingStarted(DiscoverySessionRecordingStartedEvent event) {
        broadcast(DiscoverySessionNotificationMapper.toProjectMessage(event));
    }

    @ApplicationModuleListener
    void onRecordingPaused(DiscoverySessionRecordingPausedEvent event) {
        broadcast(DiscoverySessionNotificationMapper.toProjectMessage(event));
    }

    @ApplicationModuleListener
    void onRecordingResumed(DiscoverySessionRecordingResumedEvent event) {
        broadcast(DiscoverySessionNotificationMapper.toProjectMessage(event));
    }

    @ApplicationModuleListener
    void onRecordingStopped(DiscoverySessionRecordingStoppedEvent event) {
        broadcast(DiscoverySessionNotificationMapper.toProjectMessage(event));
    }

    private void broadcast(SessionLifecycleMessage message) {
        log.debug("Notifying {} for session {} on project topic {}",
                message.type(), message.sessionId(), message.projectId());
        notifier.broadcast(ProjectTopics.sessionsOf(message.projectId()), message);
    }
}
