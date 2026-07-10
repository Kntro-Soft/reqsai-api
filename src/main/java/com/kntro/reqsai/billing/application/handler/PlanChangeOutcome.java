package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.domain.model.Subscription;
import org.jspecify.annotations.Nullable;

/**
 * Result of an upgrade attempt.
 * <ul>
 *   <li>{@code activated=true} — the plan is live now (fake gateway); {@link #subscription()} reflects
 *       the new plan and {@link #checkoutUrl()} is null.</li>
 *   <li>{@code activated=false} — a hosted checkout is required (Stripe); {@link #checkoutUrl()} holds
 *       the redirect URL and {@link #subscription()} still shows the pre-upgrade plan until the webhook
 *       confirms payment.</li>
 * </ul>
 *
 * @param subscription the current subscription aggregate
 * @param activated    whether the plan was activated synchronously
 * @param checkoutUrl  hosted-checkout URL to redirect to (present only when {@code activated=false})
 */
public record PlanChangeOutcome(
        Subscription subscription,
        boolean activated,
        @Nullable String checkoutUrl
) {}
