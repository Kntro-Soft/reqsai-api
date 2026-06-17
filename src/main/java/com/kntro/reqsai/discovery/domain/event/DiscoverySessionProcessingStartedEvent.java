package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a session transitions to {@code PROCESSING} — AI extraction has begun. */
public record DiscoverySessionProcessingStartedEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionProcessingStartedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionProcessingStartedEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
