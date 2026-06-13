package com.kntro.reqsai.workspace.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an organization is created and its tenant schema provisioned. Billing listens to assign
 * the FREE plan; infrastructure may seed the demo project.
 */
public record OrganizationCreatedEvent(UUID organizationId, UUID ownerId, String slug, Instant occurredAt)
        implements DomainEvent {

    public static OrganizationCreatedEvent of(UUID organizationId, UUID ownerId, String slug) {
        return new OrganizationCreatedEvent(organizationId, ownerId, slug, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return organizationId;
    }
}
