package com.kntro.reqsai.billing.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a previously cancelled subscription is reactivated.
 */
public record SubscriptionReactivatedEvent(
        UUID subscriptionId,
        UUID organizationId,
        String planType,
        Instant occurredAt
) implements DomainEvent {

    public static SubscriptionReactivatedEvent of(UUID subscriptionId, UUID organizationId, String planType) {
        return new SubscriptionReactivatedEvent(subscriptionId, organizationId, planType, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return subscriptionId;
    }
}
