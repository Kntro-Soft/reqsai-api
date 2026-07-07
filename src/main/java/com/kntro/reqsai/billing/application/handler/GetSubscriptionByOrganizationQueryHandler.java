package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.application.query.GetSubscriptionByOrganizationQuery;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.billing.domain.model.Subscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query handler that retrieves a subscription for a given organization ID.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetSubscriptionByOrganizationQueryHandler {

    private final SubscriptionRepositoryPort subscriptions;

    public Subscription handle(GetSubscriptionByOrganizationQuery query) {
        return subscriptions.findByOrganizationId(query.organizationId())
                .orElseThrow(() -> BillingExceptions.subscriptionNotFoundByOrganization(query.organizationId()));
    }
}
