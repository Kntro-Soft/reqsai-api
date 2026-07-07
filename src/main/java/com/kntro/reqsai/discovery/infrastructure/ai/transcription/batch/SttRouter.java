package com.kntro.reqsai.discovery.infrastructure.ai.transcription.batch;

import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.batch.strategy.AssemblyAiAdapter;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.batch.strategy.DeepgramAdapter;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.batch.strategy.WhisperAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * The single {@link TranscriptionPort} registered in the application context. Selects the batch STT
 * provider at runtime based on {@code reqsai.ai.stt.batch.provider} (default: {@code whisper}).
 *
 * <ul>
 *   <li>{@code whisper}    — self-hosted or OpenAI Whisper; no diarization
 *   <li>{@code deepgram}   — Deepgram pre-recorded API; diarization + confidence (not yet implemented)
 *   <li>{@code assemblyai} — AssemblyAI async API; diarization + utterances (not yet implemented)
 * </ul>
 *
 * <p>Not a {@code @Component} — instantiated by {@code TranscriptionConfiguration} with
 * {@code @ConditionalOnMissingBean} so tests can replace it with a stub. The {@code provider}
 * value is injected via constructor (not {@code @Value}) because the object is created with
 * {@code new} inside a {@code @Bean} method.
 */
@Slf4j
public class SttRouter implements TranscriptionPort {

    private final String provider;
    private final WhisperAdapter whisper;
    private final DeepgramAdapter deepgram;
    private final AssemblyAiAdapter assemblyAi;

    public SttRouter(String provider, WhisperAdapter whisper, DeepgramAdapter deepgram, AssemblyAiAdapter assemblyAi) {
        this.provider = provider;
        this.whisper = whisper;
        this.deepgram = deepgram;
        this.assemblyAi = assemblyAi;
    }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String filename) {
        log.debug("Routing transcription to provider '{}' for file '{}'", provider, filename);
        return switch (provider) {
            case "deepgram"   -> deepgram.transcribe(audio, filename);
            case "assemblyai" -> assemblyAi.transcribe(audio, filename);
            default -> whisper.transcribe(audio, filename);
        };
    }
}
