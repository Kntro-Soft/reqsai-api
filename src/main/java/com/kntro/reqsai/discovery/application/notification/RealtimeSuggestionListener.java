package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.domain.event.TranscriptSegmentAppendedEvent;
import com.kntro.reqsai.shared.application.listener.TenantAwareModuleListener;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Triggers realtime user-story suggestions after every N finalized transcript segments.
 *
 * <p>Listens to {@link TranscriptSegmentAppendedEvent} and fires when {@code isFinal=true}
 * and {@code sequence % suggestionWindow == 0} (e.g. at segments 5, 10, 15 for a window of 5).
 * Partial/hypothesis segments ({@code isFinal=false}) are ignored — they have not yet been
 * persisted to the DB and would produce noisy or incomplete suggestions.
 *
 * <p>Exceptions are caught and logged rather than propagated, so a generation failure cannot
 * disrupt the live transcript pipeline. Tenant context is restored via
 * {@link TenantContext#runWith}
 * using coordinates carried by the event — Spring Modulith opens its {@code REQUIRES_NEW}
 * transaction before the listener method runs, so the context must be set explicitly here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
    class RealtimeSuggestionListener extends TenantAwareModuleListener {

    private final RealtimeSuggestionService suggestionService;

    @Value("${discovery.realtime.suggestion-window:5}")
    private int suggestionWindow;

    @ApplicationModuleListener
    void onSegmentAppended(TranscriptSegmentAppendedEvent event) {
        if (!event.isFinal()) return;
        if (event.sequence() % suggestionWindow != 0) return;

        log.debug("Triggering realtime suggestion at segment #{} for session {}", event.sequence(), event.sessionId());
        try {
            withTenant(event, () -> suggestionService.suggest(event.sessionId()));
        } catch (Exception e) {
            log.error("Realtime suggestion failed for session {}: {}", event.sessionId(), e.getMessage(), e);
        }
    }
}
