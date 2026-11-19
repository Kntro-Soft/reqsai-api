package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A fragment of a meeting a story comes from, with the moment it was said")
public record StorySourceFragmentResponse(

        @Schema(description = "Fragment identifier", example = "019756a0-1234-7abc-8def-000000000020")
        UUID id,

        @Schema(description = "Session the fragment was said in", example = "019756a0-1234-7abc-8def-000000000001")
        UUID sessionId,

        @Schema(description = "Title of that session; null when the session no longer exists", nullable = true,
                example = "Kickoff con el restaurante")
        @Nullable String sessionTitle,

        @Schema(description = "When that session started; null when the session no longer exists", nullable = true)
        @Nullable Instant sessionStartedAt,

        @Schema(description = "Verbatim fragment of the conversation the story is based on",
                example = "Necesito que el comensal pueda reservar una mesa en línea")
        String quote,

        @Schema(description = "Transcript text of the segments the quote runs across; null when it was not located",
                nullable = true)
        @Nullable String transcriptText,

        @Schema(description = "Sequence of the first transcript segment; null when the quote was not located",
                nullable = true, example = "12")
        @Nullable Integer startSequence,

        @Schema(description = "Sequence of the last transcript segment; null when the quote was not located",
                nullable = true, example = "13")
        @Nullable Integer endSequence,

        @Schema(description = "Moment of the meeting (ms from the start of the recording) where the fragment "
                + "begins; null when the quote was not located", nullable = true, example = "754000")
        @Nullable Long startMs,

        @Schema(description = "Moment of the meeting (ms from the start of the recording) where the fragment "
                + "ends; null when the quote was not located", nullable = true, example = "761500")
        @Nullable Long endMs,

        @Schema(description = "Wall-clock instant the fragment was said (session start plus startMs); null when "
                + "the quote was not located", nullable = true)
        @Nullable Instant occurredAt,

        @Schema(description = "Who said it: the name the analyst gave the speaker, or \"Hablante N\"; null when "
                + "the transcript has no speakers", nullable = true, example = "Ana Torres")
        @Nullable String speakerName
) {
}
