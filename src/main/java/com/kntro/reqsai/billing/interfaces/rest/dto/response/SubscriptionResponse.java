package com.kntro.reqsai.billing.interfaces.rest.dto.response;

import java.time.Instant;

/**
 * REST response DTO for a Subscription.
 */
public record SubscriptionResponse(
        String id,
        String organizationId,
        String planType,
        String status,
        String provider,
        String providerExternalId,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        long tokenQuotaUsed,
        Instant cancelledAt
) {}
