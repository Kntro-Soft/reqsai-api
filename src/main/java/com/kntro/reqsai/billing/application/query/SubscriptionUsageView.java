package com.kntro.reqsai.billing.application.query;

import com.kntro.reqsai.billing.application.port.AiUsageLedgerPort;
import com.kntro.reqsai.billing.domain.model.MeetingEstimate;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.QuotaWarningLevel;
import org.jspecify.annotations.Nullable;

/**
 * Read model for the subscription usage/dashboard endpoint: the subscription aggregate plus the
 * resolved plan limits, display pricing, and where the period's tokens went.
 *
 * @param subscription      the subscription aggregate
 * @param maxTokensPerMonth the plan's monthly token allowance
 * @param tokensUsed        tokens counted in the current period (zero once the stored period has elapsed)
 * @param warningLevel      how far into the allowance the organization is
 * @param breakdown         the period's usage by feature and by project, from the usage ledger
 * @param meetingEstimate   minutes of live meeting the remaining allowance covers
 * @param priceCents        display price of the current plan in minor currency units
 * @param currency          ISO-4217 currency code
 * @param stripePriceId     the external price id for the current plan (null for FREE / unconfigured)
 */
public record SubscriptionUsageView(
        Subscription subscription,
        long maxTokensPerMonth,
        long tokensUsed,
        QuotaWarningLevel warningLevel,
        AiUsageLedgerPort.Summary breakdown,
        MeetingEstimate meetingEstimate,
        long priceCents,
        String currency,
        @Nullable String stripePriceId
) {}
