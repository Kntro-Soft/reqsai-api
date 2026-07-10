package com.kntro.reqsai.billing.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a subscription reverts to the FREE plan (e.g. the external paid subscription ended
 * or was deleted at the payment provider).
 */
public record SubscriptionDowngradedEvent(
        UUID subscriptionId,
        UUID organizationId,
        String previousPlanType,
        Instant occurredAt
) implements DomainEvent {

    public static SubscriptionDowngradedEvent of(UUID subscriptionId, UUID organizationId, String previousPlanType) {
        return new SubscriptionDowngradedEvent(subscriptionId, organizationId, previousPlanType, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return subscriptionId;
    }
}
