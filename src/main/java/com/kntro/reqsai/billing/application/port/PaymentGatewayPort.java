package com.kntro.reqsai.billing.application.port;

import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;

/**
 * Output port abstracting the external payment provider.
 * <p>
 * Two adapters implement it, selected by {@code reqsai.billing.payment-provider}: a synchronous
 * <strong>fake</strong> gateway (local/CI — activates the plan immediately, never charges) and the
 * real <strong>Stripe</strong> gateway (returns a hosted-checkout URL; the plan is activated later
 * from the webhook). The command handlers stay identical across both by branching on
 * {@link PlanChangeResult#activatedImmediately()}.
 */
public interface PaymentGatewayPort {

    /** Identifier of the active gateway ({@code FAKE} / {@code STRIPE}) for logging and diagnostics. */
    String providerName();

    /**
     * Begins a plan change at the provider.
     *
     * @param request the target plan, amount and external price reference
     * @return either an immediate activation (fake) or a pending hosted-checkout URL (Stripe)
     */
    PlanChangeResult startPlanChange(PlanChangeRequest request);

    /**
     * Cancels the external subscription at the provider. Best-effort and idempotent: a no-op when the
     * reference is null or does not belong to this gateway.
     *
     * @param providerRef the external subscription reference stored on the aggregate
     */
    void cancelExternalSubscription(PaymentProviderRef providerRef);
}
