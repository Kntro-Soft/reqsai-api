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
            default -> throw new UnsupportedOperationException("Plan limits not yet defined for: " + planType);
        };
    }
}
