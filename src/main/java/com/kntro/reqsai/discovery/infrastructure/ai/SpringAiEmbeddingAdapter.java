package com.kntro.reqsai.discovery.infrastructure.ai;

import com.kntro.reqsai.discovery.application.port.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * {@link EmbeddingPort} backed by Spring AI's {@link EmbeddingModel}. The model is resolved lazily via
 * an {@link ObjectProvider}: when {@code ai.model.embedding=none} (the default) no model bean exists,
 * so {@link #isAvailable()} returns {@code false} and the caller skips duplicate detection (the app
 * still boots and stories are still created). With Ollama ({@code nomic-embed-text}) or Gemini
 * configured, embeddings are produced for real.
 */
@Component
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private final ObjectProvider<EmbeddingModel> embeddingModel;

    public SpringAiEmbeddingAdapter(ObjectProvider<EmbeddingModel> embeddingModel) {
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
