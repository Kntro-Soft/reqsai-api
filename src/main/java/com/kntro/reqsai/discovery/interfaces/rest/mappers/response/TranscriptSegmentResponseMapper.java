package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.TranscriptSegmentResponse;

import java.time.Instant;

/**
 * Maps a {@link TranscriptSegment} to a {@link TranscriptSegmentResponse}, computing the absolute
 * {@code occurredAt} instant of the segment from its owning session.
 *
 * <p><strong>{@code occurredAt} derivation</strong> (documented per the task):
 * <ol>
 *   <li>{@code session.startedAt + startMs} — the recording anchor; {@code startedAt} is set both on
 *       construction and on {@code startRecording}, so this is the normal path.</li>
 *   <li>If {@code startedAt} is {@code null}, the segment's own persisted {@code createdAt} (the
 *       {@code transcript_segments.created_at} column, written when the finalized row was saved).</li>
 *   <li>If the segment has no {@code createdAt} either (never persisted), {@code session.createdAt +
 *       startMs}.</li>
 * </ol>
 */
public final class TranscriptSegmentResponseMapper {

    private TranscriptSegmentResponseMapper() {
    }

    public static TranscriptSegmentResponse toResponse(TranscriptSegment segment, DiscoverySession session) {
        return new TranscriptSegmentResponse(
                segment.getSequence(),
                segment.getText(),
                segment.getSpeakerLabel(),
                segment.getStartMs(),
                segment.getEndMs(),
                occurredAt(segment, session));
    }

    private static Instant occurredAt(TranscriptSegment segment, DiscoverySession session) {
        Instant anchor = session.getStartedAt();
        if (anchor != null) {
            return anchor.plusMillis(segment.getStartMs());
        }
        if (segment.getCreatedAt() != null) {
            return segment.getCreatedAt();
        }
        return session.getCreatedAt().plusMillis(segment.getStartMs());
    }
}
