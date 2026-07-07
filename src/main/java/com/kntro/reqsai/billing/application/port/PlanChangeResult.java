package com.kntro.reqsai.billing.application.port;

import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;
import org.jspecify.annotations.Nullable;

/**
 * Outcome of {@link PaymentGatewayPort#startPlanChange}.
 * <ul>
 *   <li><strong>Immediate</strong> ({@code activatedImmediately=true}) — the fake gateway; the handler
 *       applies {@code Subscription.upgradeTo(...)} right away with {@link #providerRef()}.</li>
 *   <li><strong>Pending</strong> ({@code activatedImmediately=false}) — Stripe; the handler returns
 *       {@link #checkoutUrl()} to the client and the plan is activated later from the webhook.</li>
 * </ul>
 *
 * @param activatedImmediately whether the plan is already active (no external checkout needed)
 * @param providerRef          external reference to persist (present for immediate activation)
 * @param checkoutUrl          hosted-checkout URL to redirect the user to (present when pending)
 */
public record PlanChangeResult(
        boolean activatedImmediately,
        @Nullable PaymentProviderRef providerRef,
        @Nullable String checkoutUrl
) {

    public static PlanChangeResult activated(PaymentProviderRef providerRef) {
        return new PlanChangeResult(true, providerRef, null);
    }

    public static PlanChangeResult pendingCheckout(String checkoutUrl) {
        return new PlanChangeResult(false, null, checkoutUrl);
    }
}
