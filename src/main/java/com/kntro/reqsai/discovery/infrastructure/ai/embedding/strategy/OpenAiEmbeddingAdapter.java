package com.kntro.reqsai.discovery.infrastructure.ai.embedding.strategy;

import com.kntro.reqsai.discovery.application.port.EmbeddingPort;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link EmbeddingPort} backed by OpenAI {@code text-embedding-3-small} via Spring AI.
 * Uses {@link OpenAiEmbeddingModel} directly so it can be active independently of
 * {@code spring.ai.model.embedding}.
 *
 * <p>Configure {@code OPENAI_API_KEY} and set
 * {@code spring.ai.openai.embedding.options.dimensions=768} to match the pgvector schema.
 * Set {@code reqsai.ai.embedding.provider=openai} to activate.
 *
 * <p>Not a Spring bean — instantiated by {@code EmbeddingConfiguration}.
 */
public class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private final ObjectProvider<OpenAiEmbeddingModel> embeddingModel;

    public OpenAiEmbeddingAdapter(ObjectProvider<OpenAiEmbeddingModel> embeddingModel) {
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
