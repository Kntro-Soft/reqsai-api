package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Port for <strong>streaming</strong> speech-to-text: an open, long-lived session that consumes audio
 * frames as they arrive and emits transcript events (partial and final) back through a listener — the
 * real-time counterpart to the batch {@code TranscriptionPort}.
 *
 * <p>The infrastructure WebSocket handler opens one session per connection, forwards binary audio to {@link Session#sendAudio},
 * and reacts to {@link TranscriptEvent}s; only {@code isFinal} events are persisted as segments.
 */
public interface StreamingTranscriptionPort {

    /**
     * Opens a streaming transcription session.
     *
     * @param context  session metadata (which discovery session, expected language)
     * @param listener receives transcript events as the provider produces them
     * @return a handle to push audio and close the stream
     */
    Session open(Context context, Listener listener);

    /** Per-connection streaming handle. Closing it flushes any pending audio and releases the provider. */
    interface Session extends AutoCloseable {

        /** Forwards a chunk of raw audio (provider-specific encoding) to the recognizer. */
        void sendAudio(byte[] frame);

        @Override
        void close();
    }

    /** Callback invoked (possibly off the request thread) for each transcript the provider emits. */
    @FunctionalInterface
    interface Listener {
        void onTranscript(TranscriptEvent event);
    }

    /**
     * Context for a streaming session.
     *
     * @param sessionId discovery session being transcribed
     * @param language  BCP-47 language hint (e.g. {@code es-PE}); may be {@code null} for auto-detect
     */
    record Context(UUID sessionId, @Nullable String language) {}

    /**
     * A transcript fragment from the provider.
     *
     * @param text    recognized text
     * @param speaker diarization label, or {@code null} when unavailable
     * @param startMs start offset from stream start, in milliseconds
     * @param endMs   end offset from stream start, in milliseconds
     * @param isFinal {@code true} for a stabilized segment to persist; {@code false} for an interim hypothesis
     */
    record TranscriptEvent(String text, @Nullable String speaker, long startMs, long endMs, boolean isFinal) {}
}
