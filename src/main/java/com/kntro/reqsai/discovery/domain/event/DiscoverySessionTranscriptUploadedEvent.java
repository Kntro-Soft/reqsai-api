package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Raised when a transcript is attached to a session, and it transitions to {@code STOPPED}. */
public record DiscoverySessionTranscriptUploadedEvent(UUID sessionId, UUID projectId, Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionTranscriptUploadedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionTranscriptUploadedEvent(sessionId, projectId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
