package com.kntro.reqsai.billing.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a subscription is assigned (typically the default free plan on organization creation).
 */
public record SubscriptionAssignedEvent(
        UUID subscriptionId,
        UUID organizationId,
        String planType,
        Instant occurredAt
) implements DomainEvent {

    public static SubscriptionAssignedEvent of(UUID subscriptionId, UUID organizationId, String planType) {
        return new SubscriptionAssignedEvent(subscriptionId, organizationId, planType, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return subscriptionId;
    }
}
