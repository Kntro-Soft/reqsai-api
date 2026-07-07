package com.kntro.reqsai.billing.api;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Public integration event announcing that an organization's active plan changed (upgrade or
 * downgrade). Exposed on the {@code billing::api} named interface so other bounded contexts — notably
 * Workspace, which mirrors the plan's operational limits on its Organization aggregate — can react
 * without importing Billing internals.
 *
 * @param organizationId the organization whose plan changed
 * @param planType       the new active plan name ({@code FREE}, {@code PRO}, {@code ENTERPRISE})
 * @param occurredAt     when the change happened
 */
public record SubscriptionPlanChangedIntegrationEvent(
        UUID organizationId,
        String planType,
        Instant occurredAt
) implements Serializable {

    public static SubscriptionPlanChangedIntegrationEvent of(UUID organizationId, String planType) {
        return new SubscriptionPlanChangedIntegrationEvent(organizationId, planType, Instant.now());
    }
}
