package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.ReactivateSubscriptionCommand;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.billing.domain.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@link ReactivateSubscriptionCommand}: resumes a previously cancelled paid subscription.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReactivateSubscriptionCommandHandler {

    private final SubscriptionRepositoryPort subscriptions;

    public Subscription handle(ReactivateSubscriptionCommand command) {
        Subscription subscription = subscriptions.findByOrganizationId(command.organizationId())
                .orElseThrow(() -> BillingExceptions.subscriptionNotFoundByOrganization(command.organizationId()));

        subscription.reactivate();
        subscriptions.save(subscription);
        log.info("Subscription {} reactivated for org {}", subscription.getId(), command.organizationId());
        return subscription;
    }
}
