package com.kntro.reqsai.workspace.api;

/**
 * Read-only view of a glossary term exposed to other bounded contexts.
 * Only the text fields are included — embeddings stay inside the workspace BC.
 */
public record GlossaryTermSnapshot(String term, String definition) {}
