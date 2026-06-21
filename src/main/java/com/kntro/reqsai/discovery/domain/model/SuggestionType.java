package com.kntro.reqsai.discovery.domain.model;

/**
 * Classifies what kind of action the AI is proposing to the analyst.
 *
 * <ul>
 *   <li>{@link #NEW_STORY} — a brand-new requirement detected in the transcript.</li>
 *   <li>{@link #UPDATE_STORY} — a near-duplicate of an existing story; the AI proposes merging or
 *       updating the existing one instead of creating a duplicate.</li>
 *   <li>{@link #EDGE_CASE} — a boundary or exceptional-flow scenario that belongs as an acceptance
 *       criterion on an existing story rather than as a standalone story.</li>
 *   <li>{@link #CLARIFYING_QUESTION} — the transcript is ambiguous; the AI cannot extract a
 *       requirement without more context and surfaces a question for the analyst to answer.</li>
 * </ul>
 */
public enum SuggestionType {
    NEW_STORY,
    UPDATE_STORY,
    EDGE_CASE,
    CLARIFYING_QUESTION
}
