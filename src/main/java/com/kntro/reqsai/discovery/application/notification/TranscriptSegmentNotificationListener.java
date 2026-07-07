package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.TranscriptSegmentAppendedEvent;
import com.kntro.reqsai.discovery.interfaces.notification.mappers.TranscriptSegmentNotificationMapper;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Bridges live transcript segments to WebSocket notifications (the transcript-out half of streaming
 * capture). Each {@link TranscriptSegmentAppendedEvent} is pushed to the session topic so the client
 * renders the running transcript incrementally as the recognizer finalizes segments.
 *
 * <p>Like the other notification listeners, runs after commit and never throws (the {@link
 * RealtimeNotifier} swallows transport errors) so a dropped frame can't disrupt capture.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TranscriptSegmentNotificationListener {

    private final RealtimeNotifier notifier;

    @ApplicationModuleListener
    void onSegmentAppended(TranscriptSegmentAppendedEvent event) {
        log.debug("Notifying transcript segment #{} for session {}", event.sequence(), event.sessionId());
        notifier.broadcast(SessionTopics.of(event.sessionId()), TranscriptSegmentNotificationMapper.toMessage(event));
    }
}
