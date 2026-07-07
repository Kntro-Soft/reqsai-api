package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStoppedEvent;
import com.kntro.reqsai.discovery.domain.event.TranscriptSegmentAppendedEvent;
import com.kntro.reqsai.shared.application.listener.TenantAwareModuleListener;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Triggers realtime user-story suggestions as a live session's transcript accrues.
 *
 * <p>Fires on every finalized segment ({@code isFinal=true}); {@link RealtimeSuggestionService}
 * itself decides whether enough new transcripts have accrued past the watermark to generate, so the
 * cadence is content-driven instead of a rigid every-N-segments rule (which cut topics mid-stream
 * and could skip the tail). On stop, a final flush processes whatever is left.
 *
 * <p>Exceptions are caught and logged rather than propagated, so a generation failure cannot
 * disrupt the live transcript pipeline. Tenant context is restored via {@link TenantContext#runWith}
 * using coordinates carried by the event — Spring Modulith opens its {@code REQUIRES_NEW}
 * transaction before the listener method runs, so the context must be set explicitly here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
    class RealtimeSuggestionListener extends TenantAwareModuleListener {

    private final RealtimeSuggestionService suggestionService;

    @ApplicationModuleListener
    void onSegmentAppended(TranscriptSegmentAppendedEvent event) {
        if (!event.isFinal()) return;
        try {
            withTenant(event, () -> suggestionService.suggest(event.sessionId()));
        } catch (Exception e) {
            log.error("Realtime suggestion failed for session {}: {}", event.sessionId(), e.getMessage(), e);
        }
    }

    /** On stop, flush the remaining transcript tail so the end of the meeting is not dropped. */
    @ApplicationModuleListener
    void onRecordingStopped(DiscoverySessionRecordingStoppedEvent event) {
        try {
            withTenant(event, () -> suggestionService.suggest(event.sessionId(), true));
        } catch (Exception e) {
            log.error("Realtime suggestion flush failed for session {}: {}", event.sessionId(), e.getMessage(), e);
        }
    }
}
