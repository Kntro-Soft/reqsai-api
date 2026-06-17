package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when AI extraction fails and the session transitions to {@code FAILED}. */
public record DiscoverySessionProcessingFailedEvent(UUID sessionId, UUID projectId, String reason, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionProcessingFailedEvent of(UUID sessionId, UUID projectId, String reason) {
        return new DiscoverySessionProcessingFailedEvent(sessionId, projectId, reason, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
