package com.kntro.reqsai.shared.infrastructure.ai.embedding.strategy;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.infrastructure.ai.embedding.exception.EmbeddingProviderException;

import java.util.List;

/**
 * {@link EmbeddingPort} backed by Gemini {@code text-embedding-004} via the official
 * {@code com.google.genai} SDK (already on the classpath as a transitive dependency of the chat
 * model).
 *
 * <p>Set {@code GEMINI_API_KEY} (shared with the chat model) and
 * {@code reqsai.ai.embedding.provider=gemini} to activate.
 *
 * <p>Not a Spring bean — instantiated by {@code EmbeddingConfiguration}.
 */
public class GeminiEmbeddingAdapter implements EmbeddingPort {

    private static final String EMBEDDING_MODEL = "text-embedding-004";
    private static final EmbedContentConfig EMBED_CONFIG = EmbedContentConfig.builder()
            .outputDimensionality(EmbeddingPort.DIMENSIONS)
            .taskType("SEMANTIC_SIMILARITY")
            .build();

    private final Client geminiClient;
    private final boolean available;

    public GeminiEmbeddingAdapter(String apiKey) {
        this.available = apiKey != null && !apiKey.isBlank();
        this.geminiClient = available ? Client.builder().apiKey(apiKey).build() : null;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public float[] embed(String text) {
        var response = geminiClient.models.embedContent(EMBEDDING_MODEL, text, EMBED_CONFIG);

        List<Float> embeddingValues = response.embeddings()
                .orElseThrow(() -> new EmbeddingProviderException("gemini", "no embeddings in response for model " + EMBEDDING_MODEL))
                .getFirst()
                .values()
                .orElseThrow(() -> new EmbeddingProviderException("gemini", "embedding has no values for model " + EMBEDDING_MODEL));

        return toFloatArray(embeddingValues);
    }

    private static float[] toFloatArray(List<Float> embeddingValues) {
        float[] vector = new float[embeddingValues.size()];
        for (int index = 0; index < embeddingValues.size(); index++) {
            vector[index] = embeddingValues.get(index);
        }
        return vector;
    }
}
