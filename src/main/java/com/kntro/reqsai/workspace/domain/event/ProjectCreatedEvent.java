package com.kntro.reqsai.workspace.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ProjectCreatedEvent(UUID projectId, UUID organizationId, UUID createdBy, Instant occurredAt)
        implements DomainEvent {

    public static ProjectCreatedEvent of(UUID projectId, UUID organizationId, UUID createdBy) {
        return new ProjectCreatedEvent(projectId, organizationId, createdBy, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return projectId;
    }
}
