package com.kntro.reqsai.discovery.application.query;

import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;

import java.util.UUID;

/**
 * Lists a discovery session's suggestions filtered by review status. Defaults to {@code PENDING} when
 * {@code status} is {@code null} — so callers that only want the live review queue stay
 * backward-compatible, while a past session can be replayed by asking for {@code ACCEPTED} /
 * {@code DISMISSED} decisions.
 */
public record ListPendingSuggestionsQuery(UUID sessionId, SuggestionStatus status) {

    /** Convenience constructor for the default {@code PENDING} queue. */
    public ListPendingSuggestionsQuery(UUID sessionId) {
        this(sessionId, null);
    }

    public SuggestionStatus statusOrDefault() {
        return status != null ? status : SuggestionStatus.PENDING;
    }
}
