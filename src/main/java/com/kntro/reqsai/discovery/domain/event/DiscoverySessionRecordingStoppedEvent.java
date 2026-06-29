package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.shared.domain.model.TenantAwareDomainEvent;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a session transitions from {@code RECORDING} or {@code PAUSED} to {@code STOPPED} via
 * live recording. Tenant-aware so the async suggestion-flush listener can query the tenant schema.
 */
public record DiscoverySessionRecordingStoppedEvent(
        UUID sessionId, UUID projectId, TenantContext.TenantSnapshot tenant, Instant occurredAt)
        implements TenantAwareDomainEvent {

    public static DiscoverySessionRecordingStoppedEvent of(UUID sessionId, UUID projectId) {
        return new DiscoverySessionRecordingStoppedEvent(sessionId, projectId, TenantContext.capture(), Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return sessionId;
    }
}
