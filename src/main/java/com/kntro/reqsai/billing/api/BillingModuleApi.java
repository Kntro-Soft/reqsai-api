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
     * Creates the FREE subscription record for a newly-created organization.
     * Idempotent: a no-op if the organization already has a subscription.
     *
     * @param organizationId the target organization
     */
    void assignFreeSubscription(UUID organizationId);
}
