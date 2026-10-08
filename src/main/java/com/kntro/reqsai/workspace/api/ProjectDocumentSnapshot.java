package com.kntro.reqsai.workspace.api;

/**
 * Read-only projection of an active client document exposed by the Workspace module: the context
 * summary the analyst approved, never the full extracted text.
 *
 * @param name         the document name
 * @param documentType the document type ({@code BUSINESS_RULES}, {@code TECHNICAL_SPEC}, …)
 * @param summary      the project context the document gives
 */
public record ProjectDocumentSnapshot(String name, String documentType, String summary) {}
