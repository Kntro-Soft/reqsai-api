package com.kntro.reqsai.billing.interfaces.rest.mappers.response;

import com.kntro.reqsai.billing.application.handler.PlanChangeOutcome;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.PlanChangeResponse;

/**
 * Maps an application {@link PlanChangeOutcome} to its REST response.
 */
public final class PlanChangeResponseMapper {

    private PlanChangeResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PlanChangeResponse toResponse(PlanChangeOutcome outcome) {
        String status = outcome.activated()
                ? PlanChangeResponse.STATUS_ACTIVATED
                : PlanChangeResponse.STATUS_CHECKOUT_REQUIRED;
        return new PlanChangeResponse(
                status,
                outcome.checkoutUrl(),
                SubscriptionResponseMapper.toResponse(outcome.subscription())
        );
    }
}
