package com.kntro.reqsai.billing.interfaces.rest.mappers.response;

import com.kntro.reqsai.billing.application.query.SubscriptionUsageView;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.SubscriptionUsageResponse;

/**
 * Maps a {@link SubscriptionUsageView} to its REST response, deriving the remaining allowance and
 * usage percentage.
 */
public final class SubscriptionUsageResponseMapper {

    private SubscriptionUsageResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static SubscriptionUsageResponse toResponse(SubscriptionUsageView view) {
        Subscription subscription = view.subscription();
        long used = subscription.getTokenQuotaUsed();
        long limit = view.maxTokensPerMonth();
        long remaining = Math.max(0L, limit - used);
        double percentage = limit > 0 ? Math.min(100.0, (used * 100.0) / limit) : 0.0;
        return new SubscriptionUsageResponse(
                subscription.getOrganizationId().toString(),
                subscription.getPlanType().name(),
                subscription.getStatus().name(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                used,
                limit,
                remaining,
                percentage,
                view.priceCents(),
                view.currency(),
                view.stripePriceId()
        );
    }
}
