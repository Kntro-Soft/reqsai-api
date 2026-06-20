package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AppendTranscriptSegmentCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one finalized transcript segment produced by the streaming STT pipeline and advances the
 * session's sequence counter.
 *
 * <p>Each segment commits in its own transaction so the {@code TranscriptSegmentAppendedEvent} raised
 * by {@link DiscoverySession#recordSegment} is published incrementally after commit — the realtime
 * listener then pushes the segment to the client live.
 *
 * <p>The session must be {@code RECORDING}; the domain method enforces that invariant and assigns the
 * monotonic sequence number. Both interim ({@code isFinal=false}) and final segments are accepted:
 * interims fire the domain event (STOMP live preview) but are not persisted to the DB — only finals
 * produce a committed row. This avoids duplicate-key conflicts on the {@code (session_id, sequence)}
 * unique constraint that would otherwise occur as Deepgram emits multiple interim results per utterance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppendTranscriptSegmentCommandHandler {

    private final DiscoverySessionRepository sessions;
    private final TranscriptSegmentRepository segments;

    @Transactional
    public void handle(AppendTranscriptSegmentCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));
        int sequence = session.recordSegment(command.text(), command.speakerLabel(), command.startMs(), command.endMs(), command.isFinal());
        if (command.isFinal()) {
            segments.save(new TranscriptSegment(command.sessionId(), sequence, command.speakerLabel(), command.text(), command.startMs(), command.endMs(), true));
            sessions.save(session);
        }
        log.debug("Appended segment #{} ({}) to session {} ({} chars)", sequence, command.isFinal() ? "final" : "partial", command.sessionId(), command.text().length());
    }
}
