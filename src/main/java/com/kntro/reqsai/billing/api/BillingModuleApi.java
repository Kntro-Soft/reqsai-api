package com.kntro.reqsai.billing.api;

import java.util.UUID;

/**
 * Public interface for the Billing module, exposed to other bounded contexts.
 */
public interface BillingModuleApi {

    /**
     * Retrieves the default free plan operational limits.
     *
     * @return the free tier limits snapshot
     */
    PlanLimitsSnapshot freePlanLimits();

    /**
     * Retrieves the operational limits for a named plan tier.
     *
     * @param planType the plan name ({@code FREE}, {@code PRO}, {@code ENTERPRISE})
     * @return the limits snapshot for that tier
     */
    PlanLimitsSnapshot planLimits(String planType);

    /**
     * Creates the FREE subscription record for a newly-created organization.
     * Idempotent: a no-op if the organization already has a subscription.
     *
     * @param organizationId the target organization
     */
    void assignFreeSubscription(UUID organizationId);

    /**
     * Records AI token consumption against an organization's current billing period. Best-effort and
     * safe to call from the AI hot path: unknown organizations are ignored, and the period rolls over
     * automatically when it has elapsed.
     *
     * @param organizationId the organization that consumed tokens
     * @param tokens         number of tokens consumed
     */
    void recordTokenConsumption(UUID organizationId, long tokens);

    /**
     * Returns whether the organization still has monthly token quota available on its current plan.
     * Fails open (returns {@code true}) when no subscription exists, so metering never blocks usage
     * for a mis-provisioned organization.
     *
     * @param organizationId the organization to check
     * @return {@code true} when consumed tokens are below the plan's monthly limit
     */
    boolean hasTokenQuotaAvailable(UUID organizationId);
}
