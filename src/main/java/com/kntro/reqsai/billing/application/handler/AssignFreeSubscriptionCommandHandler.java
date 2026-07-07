package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.AssignFreeSubscriptionCommand;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles AssignFreeSubscriptionCommand, ensuring an organization is provisioned with a default free plan.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AssignFreeSubscriptionCommandHandler {

    private final SubscriptionRepositoryPort subscriptions;

    public void handle(AssignFreeSubscriptionCommand command) {
        if (subscriptions.existsByOrganizationId(command.organizationId())) {
            log.debug("FREE subscription already exists for org {} — skipping", command.organizationId());
            return; // idempotent
        }
        Subscription subscription = new Subscription(command.organizationId());
        subscriptions.save(subscription); // publishes SubscriptionAssignedEvent upon saving
        log.info("FREE subscription {} assigned to org {}", subscription.getId(), command.organizationId());
    }
}
