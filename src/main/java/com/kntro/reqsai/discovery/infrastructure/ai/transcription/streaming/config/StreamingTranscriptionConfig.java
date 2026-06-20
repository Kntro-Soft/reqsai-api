package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.config;

import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.StreamingSttRouter;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy.AssemblyAiStreamingAdapter;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy.DeepgramStreamingAdapter;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy.WhisperLiveStreamingAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the streaming STT infrastructure: instantiates each provider adapter and registers a
 * {@link StreamingSttRouter} as the single {@link StreamingTranscriptionPort} bean — the streaming
 * mirror of {@code TranscriptionConfiguration}. {@code @ConditionalOnMissingBean} lets a test replace
 * the whole router. The {@code provider} is injected by constructor (the router is created with
 * {@code new} in a {@code @Bean} method) exactly like the batch {@code SttRouter}.
 */
@Configuration
class StreamingTranscriptionConfig {

    @Bean
    @ConditionalOnMissingBean(StreamingTranscriptionPort.class)
    StreamingTranscriptionPort streamingSttRouter(
            @Value("${reqsai.ai.stt.streaming.provider:whisperlive}") String provider,
            @Value("${DEEPGRAM_API_KEY:}") String deepgramApiKey,
            @Value("${DEEPGRAM_MODEL:nova-2}") String deepgramModel,
            @Value("${ASSEMBLYAI_API_KEY:}") String assemblyAiApiKey,
            @Value("${WHISPERLIVE_URL:ws://localhost:9090}") String whisperLiveUrl,
            @Value("${WHISPERLIVE_API_KEY:}") String whisperLiveApiKey,
            @Value("${WHISPERLIVE_MODEL:small}") String whisperLiveModel) {
        return new StreamingSttRouter(
                provider,
                new DeepgramStreamingAdapter(deepgramApiKey, deepgramModel),
                new AssemblyAiStreamingAdapter(assemblyAiApiKey),
                new WhisperLiveStreamingAdapter(whisperLiveUrl, whisperLiveApiKey, whisperLiveModel)
        );
    }
}
