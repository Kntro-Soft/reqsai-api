package com.kntro.reqsai.workspace.domain.event;

import com.kntro.reqsai.shared.domain.model.TenantAwareDomainEvent;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;

import java.time.Instant;
import java.util.UUID;

public record GlossaryTermSavedEvent(
        UUID projectId,
        UUID termId,
        String term,
        String definition,
        TenantContext.TenantSnapshot tenant,
        Instant occurredAt
) implements TenantAwareDomainEvent {

    public static GlossaryTermSavedEvent of(UUID projectId, UUID termId, String term, String definition) {
        return new GlossaryTermSavedEvent(projectId, termId, term, definition,
                TenantContext.capture(), Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return projectId;
    }
}
