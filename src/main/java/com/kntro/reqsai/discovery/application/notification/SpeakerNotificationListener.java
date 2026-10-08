package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.SessionSpeakerUpdatedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.mappers.SpeakerNotificationMapper;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Pushes a speaker's new name or side to everyone viewing the session, so the segments of that speaker are
 * relabelled live (US40). Runs after commit; the {@link RealtimeNotifier} swallows transport errors.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SpeakerNotificationListener {

    private final RealtimeNotifier notifier;

    @ApplicationModuleListener
    void onSpeakerUpdated(SessionSpeakerUpdatedEvent event) {
        log.debug("Broadcasting SPEAKER_UPDATED for speaker {} of session {}", event.speakerLabel(), event.sessionId());
        notifier.broadcast(SessionTopics.of(event.sessionId()), SpeakerNotificationMapper.toMessage(event));
    }
}
