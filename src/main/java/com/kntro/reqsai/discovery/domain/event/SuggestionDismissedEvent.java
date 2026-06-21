package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record SuggestionDismissedEvent(
        UUID suggestionId,
        UUID sessionId,
        UUID projectId,
        SuggestionType type,
        Instant occurredAt
) implements DomainEvent {

    public static SuggestionDismissedEvent of(Suggestion s) {
        return new SuggestionDismissedEvent(
                s.getId(), s.getSessionId(), s.getProjectId(), s.getType(), Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return suggestionId;
    }
}
