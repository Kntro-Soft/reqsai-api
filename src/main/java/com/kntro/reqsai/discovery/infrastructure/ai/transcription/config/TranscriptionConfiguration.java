package com.kntro.reqsai.discovery.infrastructure.ai.transcription.config;

import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.SttRouter;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.strategy.AssemblyAiAdapter;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.strategy.DeepgramAdapter;
import com.kntro.reqsai.discovery.infrastructure.ai.transcription.strategy.WhisperAdapter;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.springframework.web.client.RestClient.create;

/**
 * Wires the STT infrastructure: instantiates each provider adapter and registers an {@link SttRouter}
 * as the single {@link TranscriptionPort} bean — unless a test (or another module) already provided
 * one ({@code @ConditionalOnMissingBean} on a {@code @Bean} method is the supported Spring Boot pattern).
 */
@Configuration
class TranscriptionConfiguration {

    @Bean
    @ConditionalOnMissingBean(TranscriptionPort.class)
    TranscriptionPort sttRouter(
            ObjectProvider<OpenAiAudioTranscriptionModel> whisperModel,
            @Value("${reqsai.ai.stt.provider:whisper}") String provider,
            @Value("${DEEPGRAM_API_KEY:}") String deepgramApiKey,
            @Value("${ASSEMBLYAI_API_KEY:}") String assemblyAiApiKey) {
        return new SttRouter(
                provider,
                new WhisperAdapter(whisperModel),
                new DeepgramAdapter(deepgramApiKey),
                new AssemblyAiAdapter(create(), assemblyAiApiKey)
        );
    }
}
