package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a session transitions from {@code RECORDING} or {@code PAUSED} to {@code STOPPED} via live recording. */
public record DiscoverySessionRecordingStoppedEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionRecordingStoppedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionRecordingStoppedEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
