package com.kntro.reqsai.billing.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a paid subscription is cancelled by the organization owner.
 */
public record SubscriptionCancelledEvent(
        UUID subscriptionId,
        UUID organizationId,
        String planType,
        Instant occurredAt
) implements DomainEvent {

    public static SubscriptionCancelledEvent of(UUID subscriptionId, UUID organizationId, String planType) {
        return new SubscriptionCancelledEvent(subscriptionId, organizationId, planType, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return subscriptionId;
    }
}
