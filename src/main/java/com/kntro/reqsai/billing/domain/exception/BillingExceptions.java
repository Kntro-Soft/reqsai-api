package com.kntro.reqsai.billing.domain.exception;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

import java.util.UUID;

/**
 * Factory for billing-context domain exceptions.
 */
public final class BillingExceptions {

    private BillingExceptions() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static EntityNotFoundException subscriptionNotFoundByOrganization(UUID organizationId) {
        return new EntityNotFoundException(
                BillingError.SUBSCRIPTION_NOT_FOUND,
                "Subscription not found for organization: " + organizationId
        );
    }

    public static DomainException subscriptionAlreadyExists(UUID organizationId) {
        return new DomainException(
                BillingError.SUBSCRIPTION_ALREADY_EXISTS,
                "Subscription already exists for organization: " + organizationId
        );
    }
}
