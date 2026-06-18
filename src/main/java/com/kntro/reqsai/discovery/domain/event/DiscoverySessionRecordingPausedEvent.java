package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a session transitions from {@code RECORDING} to {@code PAUSED}. */
public record DiscoverySessionRecordingPausedEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionRecordingPausedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionRecordingPausedEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
