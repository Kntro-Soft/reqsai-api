package com.kntro.reqsai.billing.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a subscription changes to a paid plan (upgrade or plan switch).
 */
public record SubscriptionUpgradedEvent(
        UUID subscriptionId,
        UUID organizationId,
        String previousPlanType,
        String newPlanType,
        Instant occurredAt
) implements DomainEvent {

    public static SubscriptionUpgradedEvent of(UUID subscriptionId, UUID organizationId,
                                               String previousPlanType, String newPlanType) {
        return new SubscriptionUpgradedEvent(subscriptionId, organizationId, previousPlanType, newPlanType, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return subscriptionId;
    }
}
