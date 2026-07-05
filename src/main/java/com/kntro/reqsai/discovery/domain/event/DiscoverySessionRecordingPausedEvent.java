package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a session transitions from {@code RECORDING} to {@code PAUSED}. Carries the session's
 * descriptive fields so project-level realtime consumers can render the update without a DB round-trip.
 */
public record DiscoverySessionRecordingPausedEvent(
        UUID sessionId, UUID projectId, String title, String language, @Nullable Instant startedAt,
        Instant occurredAt)
        implements DomainEvent {

    public static DiscoverySessionRecordingPausedEvent of(UUID sessionId, UUID projectId,
                                                          String title, String language, @Nullable Instant startedAt) {
        return new DiscoverySessionRecordingPausedEvent(sessionId, projectId, title, language, startedAt, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
