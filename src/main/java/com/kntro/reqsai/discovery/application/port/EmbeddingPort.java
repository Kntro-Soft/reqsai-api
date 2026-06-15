package com.kntro.reqsai.discovery.application.port;

/**
 * Produces a dense embedding (768-dim) for duplicate detection, backed by Spring AI (Ollama
 * {@code nomic-embed-text} locally, Gemini in prod). {@link #isAvailable()} reflects whether an
 * embedding model is configured ({@code ai.model.embedding}); when it is not, callers skip dedup.
 */
public interface EmbeddingPort {

    /** Whether an embedding model is configured and usable right now. */
    boolean isAvailable();

    /** Embeds {@code text} into a 768-dim vector. */
    float[] embed(String text);
}
