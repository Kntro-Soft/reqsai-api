package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a session transitions from {@code DRAFT} to {@code RECORDING}. */
public record DiscoverySessionRecordingStartedEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionRecordingStartedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionRecordingStartedEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
