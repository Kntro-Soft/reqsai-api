package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a session is reset from {@code COMPLETED}, {@code FAILED}, or {@code STOPPED} back to {@code DRAFT}. */
public record DiscoverySessionResetEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionResetEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionResetEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
