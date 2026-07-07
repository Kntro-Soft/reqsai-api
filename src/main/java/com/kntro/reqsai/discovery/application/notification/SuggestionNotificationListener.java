package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.SuggestionAcceptedEvent;
import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.discovery.domain.event.SuggestionDismissedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.mappers.SuggestionNotificationMapper;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Bridges suggestion lifecycle domain events to live WebSocket notifications.
 *
 * <p>Listens to {@link SuggestionCreatedEvent}, {@link SuggestionAcceptedEvent}, and
 * {@link SuggestionDismissedEvent}, and broadcasts each to the corresponding session topic so the
 * client can show/update suggestion cards in realtime without polling.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SuggestionNotificationListener {

    private final RealtimeNotifier notifier;

    @ApplicationModuleListener
    void onSuggestionCreated(SuggestionCreatedEvent event) {
        log.debug("Broadcasting SUGGESTION_GENERATED for suggestion {} session {}",
                event.suggestionId(), event.sessionId());
        notifier.broadcast(SessionTopics.of(event.sessionId()),
                SuggestionNotificationMapper.toGeneratedMessage(event));
    }

    @ApplicationModuleListener
    void onSuggestionAccepted(SuggestionAcceptedEvent event) {
        log.debug("Broadcasting SUGGESTION_ACCEPTED for suggestion {} session {}",
                event.suggestionId(), event.sessionId());
        notifier.broadcast(SessionTopics.of(event.sessionId()),
                SuggestionNotificationMapper.toAcceptedMessage(event));
    }

    @ApplicationModuleListener
    void onSuggestionDismissed(SuggestionDismissedEvent event) {
        log.debug("Broadcasting SUGGESTION_DISMISSED for suggestion {} session {}",
                event.suggestionId(), event.sessionId());
        notifier.broadcast(SessionTopics.of(event.sessionId()),
                SuggestionNotificationMapper.toDismissedMessage(event));
    }
}
