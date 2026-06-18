package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a session transitions from {@code PAUSED} back to {@code RECORDING}. */
public record DiscoverySessionRecordingResumedEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionRecordingResumedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionRecordingResumedEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
