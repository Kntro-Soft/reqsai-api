package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a discovery session finishes AI extraction and transitions to {@code COMPLETED}. */
public record DiscoverySessionProcessingCompletedEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionProcessingCompletedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionProcessingCompletedEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
