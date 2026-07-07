package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.CancelSubscriptionCommand;
import com.kntro.reqsai.billing.application.port.PaymentGatewayPort;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.billing.domain.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@link CancelSubscriptionCommand}: cancels the aggregate (which stays usable until the
 * period ends) and best-effort cancels the external subscription at the payment provider.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CancelSubscriptionCommandHandler {

    private final SubscriptionRepositoryPort subscriptions;
    private final PaymentGatewayPort paymentGateway;

    public Subscription handle(CancelSubscriptionCommand command) {
        Subscription subscription = subscriptions.findByOrganizationId(command.organizationId())
                .orElseThrow(() -> BillingExceptions.subscriptionNotFoundByOrganization(command.organizationId()));

        subscription.cancel();
        paymentGateway.cancelExternalSubscription(subscription.getProviderRef());
        subscriptions.save(subscription);
        log.info("Subscription {} cancelled for org {}", subscription.getId(), command.organizationId());
        return subscription;
    }
}
