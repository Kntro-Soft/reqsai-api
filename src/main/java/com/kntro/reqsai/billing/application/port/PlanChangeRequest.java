package com.kntro.reqsai.billing.application.port;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Input to {@link PaymentGatewayPort#startPlanChange}: everything a gateway needs to charge for a
 * plan change without reaching back into the domain.
 *
 * @param organizationId the organization changing plan
 * @param subscriptionId the subscription aggregate id
 * @param targetPlan     the paid plan being purchased
 * @param amountCents    display amount in minor currency units
 * @param currency       ISO-4217 currency code
 * @param stripePriceId  the external price id to charge against (null for the fake gateway)
 */
public record PlanChangeRequest(
        UUID organizationId,
        UUID subscriptionId,
        PlanType targetPlan,
        long amountCents,
        String currency,
        @Nullable String stripePriceId
) {}
