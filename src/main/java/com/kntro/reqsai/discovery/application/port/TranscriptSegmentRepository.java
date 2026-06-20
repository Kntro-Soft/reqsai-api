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

    /** Removes every segment of a session (used when a session is reset). */
    void deleteAllBySessionId(UUID sessionId);
}
