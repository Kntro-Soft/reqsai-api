package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming;

import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;

/**
 * The single {@link StreamingTranscriptionPort} registered in the context. Selects the live STT provider
 * at runtime from {@code reqsai.ai.stt.streaming.provider} — the streaming counterpart of the batch
 * {@code SttRouter} (and a sibling package under {@code ai.transcription}).
 *
 * <ul>
 *   <li>{@code deepgram}    — Deepgram live streaming (interim + {@code is_final}, diarization)
 *   <li>{@code assemblyai}  — AssemblyAI Universal-Streaming v3 (immutable turns, multilingual)
 *   <li>{@code whisperlive} — self-hosted Whisper streaming (faster-whisper backend)
 * </ul>
 *
 * <p>Not a {@code @Component} — built by {@code StreamingTranscriptionConfig} with
 * {@code @ConditionalOnMissingBean}, exactly like the batch {@code SttRouter}. An unknown provider fails
 * fast with {@code transcriptionUnavailable}.
 */
@Slf4j
public class StreamingSttRouter implements StreamingTranscriptionPort {

    private final String provider;
    private final StreamingTranscriptionPort deepgram;
    private final StreamingTranscriptionPort assemblyAi;
    private final StreamingTranscriptionPort whisperLive;

    public StreamingSttRouter(String provider,
                              StreamingTranscriptionPort deepgram,
                              StreamingTranscriptionPort assemblyAi,
                              StreamingTranscriptionPort whisperLive) {
        this.provider = provider == null ? "" : provider.toLowerCase();
        this.deepgram = deepgram;
        this.assemblyAi = assemblyAi;
        this.whisperLive = whisperLive;
    }

    @Override
    public Session open(Context context, Listener listener) {
        log.debug("Routing streaming transcription to provider '{}' for session {}", provider, context.sessionId());
        return switch (provider) {
            case "deepgram" -> deepgram.open(context, listener);
            case "assemblyai" -> assemblyAi.open(context, listener);
            case "whisperlive" -> whisperLive.open(context, listener);
            default -> {
                log.error("Unknown streaming STT provider '{}' (expected deepgram | assemblyai | whisperlive)", provider);
                throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
            }
        };
    }
}
