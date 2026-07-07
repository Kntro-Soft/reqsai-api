package com.kntro.reqsai.billing.interfaces.rest.dto.response;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * REST response for the subscription usage/dashboard endpoint: plan, billing period, token
 * consumption against the plan allowance, and display pricing.
 */
public record SubscriptionUsageResponse(
        String organizationId,
        String planType,
        String status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        long tokensUsed,
        long tokensLimit,
        long tokensRemaining,
        double usagePercentage,
        long priceCents,
        String currency,
        @Nullable String stripePriceId
) {}
