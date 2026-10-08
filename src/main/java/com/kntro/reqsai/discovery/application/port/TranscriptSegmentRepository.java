package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.SpeakerSpan;
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

    /** Persists a batch of segments (the diarized utterances of an uploaded recording). */
    List<TranscriptSegment> saveAll(List<TranscriptSegment> segments);

    /** Segments of a session in ascending {@code sequence} order. */
    List<TranscriptSegment> findAllBySessionId(UUID sessionId);

    /**
     * A page of finalized segments for cursor pagination: the {@code limit} highest-sequence finals
     * whose {@code sequence} is strictly less than {@code beforeSequence}, returned DESCENDING (newest
     * first). Pass {@code Integer.MAX_VALUE} as {@code beforeSequence} for the newest page. Callers
     * reverse to ascending for rendering.
     */
    List<TranscriptSegment> findFinalBySessionIdBefore(UUID sessionId, int beforeSequence, int limit);

    /** Total number of finalized segments of a session (used to signal whether older pages remain). */
    long countFinalBySessionId(UUID sessionId);

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

    /**
     * The speaker label and time range of every finalized segment that carries a diarization label, in
     * ascending {@code sequence} order. Feeds the speaker roster (numbered by first appearance) and the
     * overlapping-speech detection without loading the segment texts.
     */
    List<SpeakerSpan> findSpeakerSpans(UUID sessionId);

    /** Removes every segment of a session (used when a session is reset). */
    void deleteAllBySessionId(UUID sessionId);
}
