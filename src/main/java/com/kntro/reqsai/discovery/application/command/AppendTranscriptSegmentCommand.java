package com.kntro.reqsai.discovery.application.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Persists or updates one transcript segment produced by the streaming STT pipeline.
 * Dispatched by the audio WebSocket handler for every {@code TranscriptEvent} — both
 * partial hypotheses ({@code isFinal=false}) and committed finals ({@code isFinal=true}).
 *
 * @param sessionId    the discovery session receiving the segment
 * @param text         recognized text of the segment
 * @param speakerLabel diarization label, or {@code null} when unavailable
 * @param startMs      start offset from recording start, in milliseconds
 * @param endMs        end offset from recording start, in milliseconds
 * @param isFinal      {@code true} if the text is committed; {@code false} for a live hypothesis
 */
public record AppendTranscriptSegmentCommand(
        UUID sessionId,
        String text,
        @Nullable String speakerLabel,
        long startMs,
        long endMs,
        boolean isFinal
) {}
