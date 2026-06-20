package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for {@link SessionEventType#TRANSCRIPT_SEGMENT} — a transcript segment
 * captured live during {@code RECORDING} (the transcript-out half of the streaming flow).
 *
 * <p>Emitted once per segment so the client can render the running transcript incrementally.
 * {@code isFinal=false} means the text is a live hypothesis and may be replaced; {@code isFinal=true}
 * means the text is committed and persisted — the client should lock that segment in the UI.
 *
 * @param sessionId   session being transcribed
 * @param sequence    monotonic position of the segment within the session
 * @param speakerLabel diarization label, or {@code null} when the provider gives none
 * @param text        segment text (partial or finalized)
 * @param startMs     start offset from recording start, in milliseconds
 * @param endMs       end offset from recording start, in milliseconds
 * @param isFinal     {@code true} when the text is committed
 * @param occurredAt  when the segment was appended
 */
public record SessionTranscriptSegmentMessage(
        UUID sessionId,
        int sequence,
        @Nullable String speakerLabel,
        String text,
        long startMs,
        long endMs,
        boolean isFinal,
        Instant occurredAt
) implements SessionRealtimeMessage {

    @Override
    public SessionEventType type() {
        return SessionEventType.TRANSCRIPT_SEGMENT;
    }
}
