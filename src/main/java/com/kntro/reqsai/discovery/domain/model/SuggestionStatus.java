package com.kntro.reqsai.discovery.domain.model;

/**
 * Review lifecycle of a {@link Suggestion}.
 * <pre>
 *   PENDING ──accept──▶ ACCEPTED
 *   PENDING ──dismiss─▶ DISMISSED
 * </pre>
 */
public enum SuggestionStatus {
    /** Awaiting analyst decision. */
    PENDING,
    /** Analyst accepted — a story or criterion was created/updated. */
    ACCEPTED,
    /** Analyst dismissed — no action taken. */
    DISMISSED
}
