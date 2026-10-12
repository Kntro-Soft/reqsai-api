package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.config.BillingProperties;
import com.kntro.reqsai.billing.application.port.AiUsageLedgerPort;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.application.query.GetSubscriptionUsageQuery;
import com.kntro.reqsai.billing.application.query.SubscriptionUsageView;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.billing.domain.model.MeetingEstimate;
import com.kntro.reqsai.billing.domain.model.PlanCatalog;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanLimitsValues;
import com.kntro.reqsai.billing.domain.model.valueobjects.QuotaWarningLevel;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Query handler assembling the usage view: subscription state, the plan's monthly token allowance,
 * and the configured display price for the current plan.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetSubscriptionUsageQueryHandler {

    /** How many projects the "where it went" list shows. */
    static final int TOP_PROJECTS = 5;
    /** Look-back for the measured tokens-per-meeting-minute rate, so a fresh period still has one. */
    static final Duration RATE_WINDOW = Duration.ofDays(90);

    private final SubscriptionRepositoryPort subscriptions;
    private final AiUsageLedgerPort ledger;
    private final BillingProperties billingProperties;

    public SubscriptionUsageView handle(GetSubscriptionUsageQuery query) {
        Subscription subscription = subscriptions.findByOrganizationId(query.organizationId())
                .orElseThrow(() -> BillingExceptions.subscriptionNotFoundByOrganization(query.organizationId()));

        PlanLimitsValues limits = PlanCatalog.limitsFor(subscription.getPlanType());
        BillingProperties.PlanPricing pricing = billingProperties.pricingFor(subscription.getPlanType());
        Instant now = Instant.now();
        long limit = limits.maxTokensPerMonth();
        long used = subscription.effectiveTokensUsed(now);

        AiUsageLedgerPort.Summary period = AiUsageLedgerPort.Summary.empty();
        MeetingEstimate estimate = MeetingEstimate.of(limit - used, 0, 0);
        if (ledgerBelongsTo(query.organizationId())) {
            try {
                Instant periodStart = subscription.getCurrentPeriodStart();
                period = ledger.summarize(periodStart, now.plusSeconds(1), TOP_PROJECTS);
                AiUsageLedgerPort.Summary history = ledger.summarize(now.minus(RATE_WINDOW), now.plusSeconds(1), 0);
                long liveTokens = history.tokensOf("LIVE_SUGGESTIONS") + history.tokensOf("FINAL_STORIES");
                estimate = MeetingEstimate.of(limit - used, liveTokens, history.transcriptionSeconds());
            } catch (RuntimeException e) {
                log.warn("Usage breakdown unavailable for org {}: {}", query.organizationId(), e.getMessage());
            }
        }
        return new SubscriptionUsageView(
                subscription,
                limit,
                used,
                QuotaWarningLevel.of(used, limit),
                period,
                estimate,
                pricing.amountCents(),
                billingProperties.currency(),
                pricing.stripePriceId()
        );
    }

    /** The ledger is the tenant schema bound to the request: it answers only for that organization. */
    private static boolean ledgerBelongsTo(UUID organizationId) {
        return organizationId.toString().equals(TenantContext.getCurrentTenant());
    }
}
