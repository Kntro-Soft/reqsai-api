package com.kntro.reqsai.discovery.application.port;

import java.util.List;

/**
 * Rich result returned by {@link TranscriptionPort}. All fields except {@code text} are optional —
 * providers that don't support a capability return {@code null} for that field.
 *
 * <p>Field availability by provider:
 * <pre>
 *   Field              Whisper   AssemblyAI   Deepgram
 *   text               ✓         ✓            ✓
 *   detectedLanguage   ✓         ✓            ✓
 *   durationMs         ✓         ✓            ✓
 *   confidence         segment   utterance    global + word
 *   segments           ✓         utterances   ✓
 *   speaker            ✗         "A"/"B"      0/1 (int)
 * </pre>
 */
public record TranscriptionResult(

        /* Full plain-text transcript — always present. */
        String text,

        /* BCP-47 language detected by the provider (e.g. {@code "es"}, {@code "en"}); null if not returned. */
        String detectedLanguage,

        /* Duration of the transcribed audio in milliseconds; 0 if not returned by the provider. */
        long durationMs,

        /* Overall transcription confidence [0, 1]; null if not returned. */
        Double confidence,

        /*
         * Speaker-annotated segments. Null when the provider doesn't support diarization (Whisper).
         * Speaker labels are provider-specific: {@code "A"/"B"} for AssemblyAI, {@code "0"/"1"} for Deepgram.
         */
        List<SpeakerSegment> segments

) {

    /** One continuous speech segment attributed to a single speaker. */
    public record SpeakerSegment(
            /* Provider-specific speaker label: {@code "A"} (AssemblyAI) or {@code "0"} (Deepgram). Null for Whisper. */
            String speaker,
            String text,
            long startMs,
            long endMs,
            /* Confidence for this segment [0, 1]; null if not returned. */
            Double confidence
    ) {}

    /** Returns true if at least one segment carries a speaker label (diarization is available). */
    public boolean hasDiarization() {
        return segments != null && segments.stream().anyMatch(s -> s.speaker() != null);
    }

    /** Factory for providers that only return plain text. */
    public static TranscriptionResult textOnly(String text, long durationMs) {
        return new TranscriptionResult(text, null, durationMs, null, null);
    }
}
