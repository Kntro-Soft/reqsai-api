package com.kntro.reqsai.shared.infrastructure.ai.embedding.strategy;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link EmbeddingPort} backed by Spring AI's generic {@link EmbeddingModel}.
 * Resolves lazily: if no model is configured ({@code spring.ai.model.embedding=none}),
 * {@link #isAvailable()} returns {@code false} and deduplication is skipped gracefully.
 * Works with any provider Spring AI autoconfigures: Gemini, Ollama, or OpenAI.
 *
 * <p>Not a Spring bean — instantiated by {@code EmbeddingConfiguration}.
 */
public class GenericEmbeddingAdapter implements EmbeddingPort {

    private final ObjectProvider<EmbeddingModel> embeddingModel;

    public GenericEmbeddingAdapter(ObjectProvider<EmbeddingModel> embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public boolean isAvailable() {
        return embeddingModel.getIfAvailable() != null;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.getObject().embed(text);
    }
}
