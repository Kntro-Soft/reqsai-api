package com.kntro.reqsai.workspace.application.port;

/**
 * AI capability that reads a client document and classifies its content into project context: a
 * summary, glossary terms (term + definition) and project constraints. The single bean is selected by
 * {@code reqsai.ai.generation.provider}; tests replace it with a deterministic stub.
 */
public interface DocumentClassificationPort {

    /** Whether a model is configured; when not, the caller skips classification. */
    boolean isAvailable();

    /**
     * Classifies the document text. The result is raw model output: callers normalize it (trim, dedupe,
     * cap) before showing it to the analyst.
     *
     * @throws RuntimeException when the model is unreachable or its reply cannot be read
     */
    DocumentClassification classify(DocumentClassificationRequest request);
}
