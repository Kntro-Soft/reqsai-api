package com.kntro.reqsai.discovery.infrastructure.ai.transcription.strategy;

import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;

/**
 * STT adapter backed by Spring AI's {@link OpenAiAudioTranscriptionModel}. Compatible with both the
 * official OpenAI Whisper API and self-hosted servers (e.g. {@code faster-whisper-server}) via
 * {@code WHISPER_BASE_URL}. Returns text + detected language + duration; speaker segments are null
 * because Whisper does not support diarization.
 *
 * <p>Not a Spring bean — instantiated and held by {@code SttRouter}.
 */
public class WhisperAdapter {

    private final ObjectProvider<OpenAiAudioTranscriptionModel> model;

    public WhisperAdapter(ObjectProvider<OpenAiAudioTranscriptionModel> model) {
        this.model = model;
    }

    public TranscriptionResult transcribe(byte[] audio, String filename) {
        OpenAiAudioTranscriptionModel transcriptionModel = model.getIfAvailable();
        if (transcriptionModel == null) {
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }
        AudioTranscriptionResponse response = transcriptionModel.call(
                new AudioTranscriptionPrompt(namedResource(audio, filename)));
        String text = response.getResult().getOutput();
        String language = extractString(response);
        long durationMs = extractDurationMs(response);
        return new TranscriptionResult(text, language, durationMs, null, null);
    }

    private static String extractString(AudioTranscriptionResponse response) {
        Object raw = response.getMetadata().get("language");
        return raw instanceof String s ? s : null;
    }

    private static long extractDurationMs(AudioTranscriptionResponse response) {
        Object raw = response.getMetadata().get("duration");
        return raw instanceof Number n ? Math.round(n.doubleValue() * 1000.0) : 0L;
    }

    private static ByteArrayResource namedResource(byte[] audio, String filename) {
        return new ByteArrayResource(audio) {
            @Override
            public String getFilename() { return filename; }
        };
    }
}
