package com.kntro.reqsai.workspace.api;

/**
 * Read-only projection of a {@code GlossaryTerm} exposed by the Workspace module.
 * Contains only the text fields needed for LLM context enrichment — embeddings stay inside the workspace BC.
 */
public record GlossaryTermSnapshot(String term, String definition) {}
