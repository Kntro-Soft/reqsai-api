package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.TenantAwareDomainEvent;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a session transitions from {@code RECORDING} or {@code PAUSED} to {@code STOPPED} via
 * live recording. Tenant-aware so the async suggestion-flush listener can query the tenant schema.
 * Carries the session's descriptive fields so project-level realtime consumers can render the
 * update without a DB round-trip.
 */
public record DiscoverySessionRecordingStoppedEvent(
        UUID sessionId, UUID projectId, String title, String language, @Nullable Instant startedAt,
        TenantContext.TenantSnapshot tenant, Instant occurredAt)
        implements TenantAwareDomainEvent {

    public static DiscoverySessionRecordingStoppedEvent of(UUID sessionId, UUID projectId,
                                                           String title, String language, @Nullable Instant startedAt) {
        return new DiscoverySessionRecordingStoppedEvent(sessionId, projectId, title, language, startedAt,
                TenantContext.capture(), Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
