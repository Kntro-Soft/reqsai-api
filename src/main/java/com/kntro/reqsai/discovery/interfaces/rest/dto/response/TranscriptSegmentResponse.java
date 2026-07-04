package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A single finalized transcript segment of a discovery session, with an absolute wall-clock timestamp
 * so the frontend can rebuild the chat timeline of a past session.
 *
 * <p>{@code occurredAt} is derived by {@code TranscriptSegmentResponseMapper}: the session's
 * {@code startedAt} (fallback {@code createdAt}) plus {@code startMs} milliseconds, or the segment's
 * own persisted {@code createdAt} when the session has no start instant at all.
 */
@Schema(description = "A finalized transcript segment with an absolute timestamp")
public record TranscriptSegmentResponse(

        @Schema(description = "Monotonic order of the segment within the session", example = "1")
        int sequence,

        @Schema(description = "Finalized transcription text of the segment")
        String text,

        @Schema(description = "Diarization speaker label when the provider supplies one", nullable = true, example = "0")
        @Nullable String speakerLabel,

        @Schema(description = "Offset (ms) from the start of the recording where this segment begins", example = "1500")
        long startMs,

        @Schema(description = "Offset (ms) from the start of the recording where this segment ends", example = "3200")
        long endMs,

        @Schema(description = "Absolute wall-clock instant the segment was spoken (ISO-8601)")
        Instant occurredAt
) {
}
