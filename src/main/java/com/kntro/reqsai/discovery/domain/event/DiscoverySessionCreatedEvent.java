package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a discovery session is created (in {@code DRAFT}). */
public record DiscoverySessionCreatedEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionCreatedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionCreatedEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
