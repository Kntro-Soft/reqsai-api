package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a transcript segment is appended to a session during live-streaming capture
 * ({@code RECORDING}). Carries the full segment payload (fat event) so the realtime listener
 * can push the transcript-out to the client without a follow-up query.
 *
 * <p>This is an intra-module event: only the notification listener within Discovery consumes it.
 * Cross-module concerns (e.g. Billing) listen to coarser-grained session events instead.
 *
 * <p>{@code isFinal=false} signals a live hypothesis for immediate WS display; {@code isFinal=true}
 * signals a committed segment that has been persisted to the DB.
 *
 * @param sessionId    originating session
 * @param sequence     monotonic position of the segment within the session
 * @param speakerLabel diarization label, or {@code null} when the provider gives none
 * @param text         segment text (partial or finalized)
 * @param startMs      start offset from recording start, in milliseconds
 * @param endMs        end offset from recording start, in milliseconds
 * @param isFinal      {@code true} when the text is committed, {@code false} for a hypothesis
 */
public record TranscriptSegmentAppendedEvent(
        UUID sessionId,
        int sequence,
        @Nullable String speakerLabel,
        String text,
        long startMs,
        long endMs,
        boolean isFinal,
        Instant occurredAt
) implements DomainEvent {

    public static TranscriptSegmentAppendedEvent of(UUID sessionId, int sequence, @Nullable String speakerLabel, String text, long startMs, long endMs, boolean isFinal) {
        return new TranscriptSegmentAppendedEvent(sessionId, sequence, speakerLabel, text, startMs, endMs, isFinal, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
