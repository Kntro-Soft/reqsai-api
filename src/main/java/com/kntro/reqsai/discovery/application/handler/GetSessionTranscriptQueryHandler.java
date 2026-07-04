package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.query.GetSessionTranscriptQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the transcript text of a discovery session.
 *
 * <p>A processed session stores the full transcript on its {@code transcript} field. A session that
 * was only ever captured live via the streaming STT pipeline never populates that field — the
 * conversation lives as individual {@link TranscriptSegment} rows persisted per finalized segment.
 * When the stored field is blank, this handler assembles the conversation from the session's final
 * segments in ascending {@code sequence} order (one segment per line) so a reloaded past session
 * still renders its conversation.
 */
@Component
@RequiredArgsConstructor
public class GetSessionTranscriptQueryHandler {

    private final DiscoverySessionRepository sessions;
    private final TranscriptSegmentRepository segments;

    @Transactional(readOnly = true)
    public String handle(GetSessionTranscriptQuery query) {
        DiscoverySession session = sessions.findById(query.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(query.sessionId()));

        String stored = session.getTranscript();
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return assembleFromSegments(query.sessionId());
    }

    private String assembleFromSegments(UUID sessionId) {
        String assembled = segments.findAllBySessionId(sessionId).stream()
                .filter(TranscriptSegment::isFinal)
                .map(TranscriptSegment::getText)
                .collect(Collectors.joining("\n"));
        return assembled.isBlank() ? null : assembled;
    }
}
