package com.kntro.reqsai.billing.interfaces.rest.dto.response;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * REST response for the subscription usage/dashboard endpoint: plan, billing period, token
 * consumption against the plan allowance, where those tokens went, and display pricing.
 *
 * <p>{@code warningLevel} is {@code NONE}, {@code WARNING_80}, {@code WARNING_90} or {@code EXHAUSTED}.
 * Token figures in {@code breakdown} are quota tokens (what counts against the allowance); the input,
 * output and cached figures are as the provider reported them.
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
        String warningLevel,
        UsageBreakdown breakdown,
        MeetingEstimate meetingEstimate,
        long priceCents,
        String currency,
        @Nullable String stripePriceId
) {

    public record UsageBreakdown(List<FeatureUsage> byFeature, List<ProjectUsage> topProjects,
                                 long transcriptionSeconds) {}

    public record FeatureUsage(String feature, long tokens, long inputTokens, long outputTokens,
                               long cachedInputTokens, long calls) {}

    public record ProjectUsage(String projectId, long tokens) {}

    /** Minutes of live meeting the remaining allowance covers, at the rate the organization measured. */
    public record MeetingEstimate(long minutesLeft, long tokensPerMinute, boolean measured) {}
}
