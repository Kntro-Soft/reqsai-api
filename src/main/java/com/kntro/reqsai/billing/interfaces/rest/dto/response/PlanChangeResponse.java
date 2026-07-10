package com.kntro.reqsai.billing.interfaces.rest.dto.response;

import org.jspecify.annotations.Nullable;

/**
 * REST response for an upgrade attempt.
 * <ul>
 *   <li>{@code status=ACTIVATED} — the plan is live; {@code subscription} reflects the new plan and
 *       {@code checkoutUrl} is null.</li>
 *   <li>{@code status=CHECKOUT_REQUIRED} — redirect the user to {@code checkoutUrl} to complete
 *       payment; the plan activates once the provider webhook confirms.</li>
 * </ul>
 *
 * @param status       {@code ACTIVATED} or {@code CHECKOUT_REQUIRED}
 * @param checkoutUrl  hosted-checkout URL to redirect to (present only for {@code CHECKOUT_REQUIRED})
 * @param subscription the current subscription state
 */
public record PlanChangeResponse(
        String status,
        @Nullable String checkoutUrl,
        SubscriptionResponse subscription
) {
    public static final String STATUS_ACTIVATED = "ACTIVATED";
    public static final String STATUS_CHECKOUT_REQUIRED = "CHECKOUT_REQUIRED";
}
