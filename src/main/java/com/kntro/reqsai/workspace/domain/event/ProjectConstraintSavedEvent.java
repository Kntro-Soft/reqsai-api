package com.kntro.reqsai.workspace.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ProjectConstraintSavedEvent(
        UUID projectId,
        UUID constraintId,
        String description,
        Instant occurredAt
) implements DomainEvent {

    public static ProjectConstraintSavedEvent of(UUID projectId, UUID constraintId, String description) {
        return new ProjectConstraintSavedEvent(projectId, constraintId, description, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return projectId;
    }
}
