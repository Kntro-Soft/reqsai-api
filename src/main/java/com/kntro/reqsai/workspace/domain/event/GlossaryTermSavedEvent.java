package com.kntro.reqsai.workspace.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record GlossaryTermSavedEvent(
        UUID projectId,
        UUID termId,
        String term,
        String definition,
        Instant occurredAt
) implements DomainEvent {

    public static GlossaryTermSavedEvent of(UUID projectId, UUID termId, String term, String definition) {
        return new GlossaryTermSavedEvent(projectId, termId, term, definition, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return projectId;
    }
}
