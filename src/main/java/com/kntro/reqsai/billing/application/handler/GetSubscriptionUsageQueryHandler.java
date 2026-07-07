package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.config.BillingProperties;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.application.query.GetSubscriptionUsageQuery;
import com.kntro.reqsai.billing.application.query.SubscriptionUsageView;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.billing.domain.model.PlanCatalog;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanLimitsValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query handler assembling the usage view: subscription state, the plan's monthly token allowance,
 * and the configured display price for the current plan.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetSubscriptionUsageQueryHandler {

    private final SubscriptionRepositoryPort subscriptions;
    private final BillingProperties billingProperties;

    public SubscriptionUsageView handle(GetSubscriptionUsageQuery query) {
        Subscription subscription = subscriptions.findByOrganizationId(query.organizationId())
                .orElseThrow(() -> BillingExceptions.subscriptionNotFoundByOrganization(query.organizationId()));

        PlanLimitsValues limits = PlanCatalog.limitsFor(subscription.getPlanType());
        BillingProperties.PlanPricing pricing = billingProperties.pricingFor(subscription.getPlanType());
        return new SubscriptionUsageView(
                subscription,
                limits.maxTokensPerMonth(),
                pricing.amountCents(),
                billingProperties.currency(),
                pricing.stripePriceId()
        );
    }
}
