package com.kntro.reqsai.shared.infrastructure.ai.embedding.config;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.infrastructure.ai.embedding.EmbeddingRouter;
import com.kntro.reqsai.shared.infrastructure.ai.embedding.strategy.GeminiEmbeddingAdapter;
import com.kntro.reqsai.shared.infrastructure.ai.embedding.strategy.GenericEmbeddingAdapter;
import com.kntro.reqsai.shared.infrastructure.ai.embedding.strategy.OpenAiEmbeddingAdapter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the embedding infrastructure: instantiates the provider adapters and registers an
 * {@link EmbeddingRouter} as the single {@link EmbeddingPort} bean — unless a test already
 * provided one ({@code @ConditionalOnMissingBean}).
 */
@Configuration
class EmbeddingConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmbeddingPort.class)
    EmbeddingPort embeddingRouter(
            ObjectProvider<EmbeddingModel> embeddingModel,
            ObjectProvider<OpenAiEmbeddingModel> openAiEmbeddingModel,
            @Value("${GEMINI_API_KEY:}") String geminiApiKey,
            @Value("${reqsai.ai.embedding.provider:auto}") String provider) {
        return new EmbeddingRouter(
                provider,
                new GenericEmbeddingAdapter(embeddingModel),
                new GeminiEmbeddingAdapter(geminiApiKey),
                new OpenAiEmbeddingAdapter(openAiEmbeddingModel)
        );
    }
}
