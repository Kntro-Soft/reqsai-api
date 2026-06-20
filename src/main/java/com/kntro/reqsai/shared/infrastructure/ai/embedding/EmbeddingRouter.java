package com.kntro.reqsai.shared.infrastructure.ai.embedding;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.infrastructure.ai.embedding.strategy.GeminiEmbeddingAdapter;
import com.kntro.reqsai.shared.infrastructure.ai.embedding.strategy.GenericEmbeddingAdapter;
import com.kntro.reqsai.shared.infrastructure.ai.embedding.strategy.OpenAiEmbeddingAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * The single {@link EmbeddingPort} registered in the application context.
 * Selects the embedding provider at runtime based on {@code reqsai.ai.embedding.provider}.
 *
 * <ul>
 *   <li>{@code auto} (default) — delegates to whatever {@code spring.ai.model.embedding} configures
 *       (Ollama, etc.)
 *   <li>{@code gemini} — Gemini {@code text-embedding-004} via SDK (768 native dims, free tier)
 *   <li>{@code openai} — OpenAI {@code text-embedding-3-small} via Spring AI (768 via dimensions param)
 * </ul>
 *
 * <p>Not a {@code @Component} — instantiated by {@code EmbeddingConfiguration} with
 * {@code @ConditionalOnMissingBean} so tests can replace it with a stub.
 */
@Slf4j
public class EmbeddingRouter implements EmbeddingPort {

    private final String provider;
    private final GenericEmbeddingAdapter generic;
    private final GeminiEmbeddingAdapter gemini;
    private final OpenAiEmbeddingAdapter openAi;

    public EmbeddingRouter(String provider, GenericEmbeddingAdapter generic, GeminiEmbeddingAdapter gemini, OpenAiEmbeddingAdapter openAi) {
        this.provider = provider;
        this.generic = generic;
        this.gemini = gemini;
        this.openAi = openAi;
    }

    @Override
    public boolean isAvailable() {
        return activeAdapter().isAvailable();
    }

    @Override
    public float[] embed(String text) {
        log.debug("Routing embedding to provider '{}'", provider);
        return activeAdapter().embed(text);
    }

    private EmbeddingPort activeAdapter() {
        return switch (provider) {
            case "openai" -> openAi;
            case "gemini" -> gemini;
            default -> generic;
        };
    }
}
