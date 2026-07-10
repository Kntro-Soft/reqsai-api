package com.kntro.reqsai.billing.application.query;

import com.kntro.reqsai.billing.domain.model.Subscription;
import org.jspecify.annotations.Nullable;

/**
 * Read model for the subscription usage/dashboard endpoint: the subscription aggregate plus the
 * resolved plan limits and display pricing.
 *
 * @param subscription     the subscription aggregate
 * @param maxTokensPerMonth the plan's monthly token allowance
 * @param priceCents       display price of the current plan in minor currency units
 * @param currency         ISO-4217 currency code
 * @param stripePriceId    the external price id for the current plan (null for FREE / unconfigured)
 */
public record SubscriptionUsageView(
        Subscription subscription,
        long maxTokensPerMonth,
        long priceCents,
        String currency,
        @Nullable String stripePriceId
) {}
