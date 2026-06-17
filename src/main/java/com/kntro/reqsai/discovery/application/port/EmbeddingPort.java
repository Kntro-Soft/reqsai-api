package com.kntro.reqsai.discovery.application.port;

/**
 * Produces a dense embedding for duplicate detection, backed by Spring AI (Ollama
 * {@code nomic-embed-text} locally, Gemini in prod). {@link #isAvailable()} reflects whether an
 * embedding model is configured; when it is not, callers skip dedup.
 */
public interface EmbeddingPort {

    /** Vector length used across the whole embedding pipeline (model output, pgvector schema, dedup). */
    int DIMENSIONS = 768;

    /** Whether an embedding model is configured and usable right now. */
    boolean isAvailable();

    /** Embeds {@code text} into a {@value #DIMENSIONS}-dim vector. */
    float[] embed(String text);
}
