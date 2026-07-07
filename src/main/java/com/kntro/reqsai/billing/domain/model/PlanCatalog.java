package com.kntro.reqsai.billing.domain.model;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanLimitsValues;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;

/**
 * Single source of truth for the numbers/limits assigned to each plan tier.
 */
public final class PlanCatalog {

    private PlanCatalog() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Resolves the limits for a specific plan tier.
     *
     * @param planType the target plan type
     * @return the configured operational limits
     */
    public static PlanLimitsValues limitsFor(PlanType planType) {
        return switch (planType) {
            case FREE -> new PlanLimitsValues(3, 25, 10, 100_000L, 50);
            case PRO -> new PlanLimitsValues(15, 200, 50, 2_000_000L, 300);
            case ENTERPRISE -> new PlanLimitsValues(200, 2_000, 500, 50_000_000L, 3_000);
        };
    }

    /**
     * A plan is purchasable when it can be assigned through the paid upgrade flow.
     * FREE is provisioned automatically on organization creation, never purchased.
     *
     * @param planType the plan tier
     * @return {@code true} for paid tiers (PRO, ENTERPRISE)
     */
    public static boolean isPurchasable(PlanType planType) {
        return planType != PlanType.FREE;
    }
}
