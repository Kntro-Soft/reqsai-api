package com.kntro.reqsai.billing.application.port;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Gateway-agnostic view of a verified payment-provider webhook event, produced by
 * {@link PaymentWebhookParserPort} and consumed by the webhook handler.
 *
 * @param eventId                provider event id (used for idempotency)
 * @param kind                   normalized event kind the handler acts on
 * @param organizationId         organization the event concerns (present for plan activation)
 * @param targetPlan             plan to activate (present for {@link Kind#PLAN_ACTIVATED})
 * @param externalSubscriptionId external subscription reference at the provider
 */
public record PaymentWebhookEvent(
        String eventId,
        Kind kind,
        @Nullable UUID organizationId,
        @Nullable PlanType targetPlan,
        @Nullable String externalSubscriptionId
) {

    /** Normalized event kinds the billing webhook handler reacts to. */
    public enum Kind {
        /** Checkout completed / paid subscription became active — activate the paid plan. */
        PLAN_ACTIVATED,
        /** The external subscription ended — revert to FREE. */
        SUBSCRIPTION_DELETED,
        /** A renewal payment failed — flag the subscription past-due. */
        PAYMENT_FAILED,
        /** An event we receive but do not act on. */
        IGNORED
    }
}
