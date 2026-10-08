package com.kntro.reqsai.workspace.application.port;

/**
 * The text read from a client document.
 *
 * @param text      the normalized text, never blank
 * @param truncated whether text beyond the extraction cap was dropped
 */
public record ExtractedText(String text, boolean truncated) {}
