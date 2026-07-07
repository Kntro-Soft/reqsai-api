package com.kntro.reqsai.workspace.domain.event;

import com.kntro.reqsai.shared.domain.model.TenantAwareDomainEvent;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;

import java.time.Instant;
import java.util.UUID;

public record ProjectConstraintSavedEvent(
        UUID projectId,
        UUID constraintId,
        String description,
        TenantContext.TenantSnapshot tenant,
        Instant occurredAt
) implements TenantAwareDomainEvent {

    public static ProjectConstraintSavedEvent of(UUID projectId, UUID constraintId, String description) {
        return new ProjectConstraintSavedEvent(projectId, constraintId, description,
                TenantContext.capture(), Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return projectId;
    }
}
