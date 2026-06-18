package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.mappers.UserStoryNotificationMapper;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionRealtimeMessage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bridges story generation domain events to live WebSocket notifications.
 *
 * <p>Listens to {@link UserStoryCreatedEvent} and
 * forwards session-scoped stories to the session topic so the client can append stories to the
 * live backlog incrementally instead of waiting for a bulk push at completion.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class StoryNotificationListener {

    private final RealtimeNotifier notifier;

    @ApplicationModuleListener
    void onStoryGenerated(UserStoryCreatedEvent event) {
        if (event.sessionId() == null) return;
        broadcast(event.sessionId(), UserStoryNotificationMapper.toMessage(event));
    }

    private void broadcast(UUID sessionId, SessionRealtimeMessage message) {
        log.debug("Notifying {} for session {}", message.type(), sessionId);
        notifier.broadcast(SessionTopics.of(sessionId), message);
    }
}
