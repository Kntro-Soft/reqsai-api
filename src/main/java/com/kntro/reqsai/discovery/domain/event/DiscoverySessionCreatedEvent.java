package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a discovery session is created (in {@code DRAFT}). Carries the session's descriptive
 * fields so project-level realtime consumers can render the new session without a DB round-trip.
 */
public record DiscoverySessionCreatedEvent(
        UUID sessionId, UUID projectId, String title, String language, @Nullable Instant startedAt,
        Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionCreatedEvent of(UUID sessionId, UUID projectId,
                                                  String title, String language, @Nullable Instant startedAt) {
        return new DiscoverySessionCreatedEvent(sessionId, projectId, title, language, startedAt, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
