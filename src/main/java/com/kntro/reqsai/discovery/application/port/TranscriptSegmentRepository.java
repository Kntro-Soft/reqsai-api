package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for {@link TranscriptSegment}. Tenant-scoped (schema resolved from the JWT
 * {@code orgId}). Segments are written incrementally during live capture and read back in order to
 * assemble the full transcript when recording stops.
 */
public interface TranscriptSegmentRepository {

    TranscriptSegment save(TranscriptSegment segment);

    /** Segments of a session in ascending {@code sequence} order. */
    List<TranscriptSegment> findAllBySessionId(UUID sessionId);

    /**
     * The {@code limit} most recent finalized segments of a session, in descending sequence order.
     * Used for realtime suggestion windowing — callers should reverse before concatenating text.
     */
    List<TranscriptSegment> findRecentFinalBySessionId(UUID sessionId, int limit);

    /**
     * Finalized segments whose {@code sequence} is greater than {@code afterSequence}, ascending.
     * Drives watermark-based realtime suggestions (only the not-yet-processed tail).
     */
    List<TranscriptSegment> findFinalBySessionIdAfter(UUID sessionId, int afterSequence);

    /** Removes every segment of a session (used when a session is reset). */
    void deleteAllBySessionId(UUID sessionId);
}
