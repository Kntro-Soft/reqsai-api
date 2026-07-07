package com.kntro.reqsai.discovery.application.port;

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
     */
    TranscriptionResult transcribe(byte[] audio, String filename);
}
