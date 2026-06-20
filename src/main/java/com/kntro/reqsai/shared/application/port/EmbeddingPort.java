package com.kntro.reqsai.shared.application.port;

/**
 * Produces a dense embedding vector, backed by Spring AI (Ollama {@code nomic-embed-text}
 * locally, Gemini / OpenAI in prod). Lives in {@code shared} so any bounded context
 * (discovery, workspace, …) can inject it without cross-BC coupling.
 *
 * <p>{@link #isAvailable()} reflects whether an embedding model is configured;
 * callers should skip embedding work when it returns {@code false}.
 */
public interface EmbeddingPort {

    /** Vector length used across the whole embedding pipeline (model output, pgvector schema, dedup). */
    int DIMENSIONS = 768;

    /** Whether an embedding model is configured and usable right now. */
    boolean isAvailable();

    /** Embeds {@code text} into a {@value #DIMENSIONS}-dim vector. */
    float[] embed(String text);
}
