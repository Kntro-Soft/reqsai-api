package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

/**
 * Converts raw audio bytes into a {@link TranscriptionResult}. The STT provider is selected by {@code reqsai.ai.stt.provider} — callers
 * only depend on this port. The active implementation is {@code SttRouter}.
 */
public interface TranscriptionPort {

    /**
     * Transcribes {@code audio} and returns the result with all available metadata.
     *
     * @param audio    raw audio bytes (MP3, WAV, M4A, etc.)
     * @param filename original filename — used by providers to infer the audio format
     * @param language ISO-639-1 language hint (e.g. {@code "es"}), or {@code null}/blank to let the
     *                 provider auto-detect
     */
    TranscriptionResult transcribe(byte[] audio, String filename, @Nullable String language);
}
