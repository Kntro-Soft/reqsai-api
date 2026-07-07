package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.UpgradeSubscriptionCommand;
import com.kntro.reqsai.billing.application.config.BillingProperties;
import com.kntro.reqsai.billing.application.port.PaymentGatewayPort;
import com.kntro.reqsai.billing.application.port.PlanChangeRequest;
import com.kntro.reqsai.billing.application.port.PlanChangeResult;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.billing.domain.model.PlanCatalog;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@link UpgradeSubscriptionCommand}: asks the payment gateway to start the plan change and,
 * for a synchronous (fake) activation, applies the upgrade to the aggregate immediately. For a real
 * gateway the plan stays unchanged and the returned checkout URL drives the client redirect; the
 * upgrade is finalised later from the webhook.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UpgradeSubscriptionCommandHandler {

    private final SubscriptionRepositoryPort subscriptions;
    private final PaymentGatewayPort paymentGateway;
    private final BillingProperties billingProperties;

    public PlanChangeOutcome handle(UpgradeSubscriptionCommand command) {
        PlanType target = command.targetPlan();
        if (target == null || !PlanCatalog.isPurchasable(target)) {
            throw BillingExceptions.planNotPurchasable(String.valueOf(target));
        }

        Subscription subscription = subscriptions.findByOrganizationId(command.organizationId())
                .orElseThrow(() -> BillingExceptions.subscriptionNotFoundByOrganization(command.organizationId()));

        BillingProperties.PlanPricing pricing = billingProperties.pricingFor(target);
        PlanChangeResult result = paymentGateway.startPlanChange(new PlanChangeRequest(
                command.organizationId(),
                subscription.getId(),
                target,
                pricing.amountCents(),
                billingProperties.currency(),
                pricing.stripePriceId()
        ));

        if (result.activatedImmediately()) {
            subscription.upgradeTo(target, result.providerRef());
            subscriptions.save(subscription);
            log.info("Subscription {} upgraded to {} for org {} via {}",
                    subscription.getId(), target, command.organizationId(), paymentGateway.providerName());
            return new PlanChangeOutcome(subscription, true, null);
        }

        log.info("Checkout started for org {} to upgrade to {} via {}; awaiting webhook confirmation",
                command.organizationId(), target, paymentGateway.providerName());
        return new PlanChangeOutcome(subscription, false, result.checkoutUrl());
    }
}
