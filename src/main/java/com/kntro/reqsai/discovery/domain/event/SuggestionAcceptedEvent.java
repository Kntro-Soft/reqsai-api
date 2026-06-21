package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record SuggestionAcceptedEvent(
        UUID suggestionId,
        UUID sessionId,
        UUID projectId,
        SuggestionType type,
        @Nullable UUID resolvedStoryId,
        Instant occurredAt
) implements DomainEvent {

    public static SuggestionAcceptedEvent of(Suggestion s) {
        return new SuggestionAcceptedEvent(
                s.getId(), s.getSessionId(), s.getProjectId(),
                s.getType(), s.getResolvedStoryId(), Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return suggestionId;
    }
}
