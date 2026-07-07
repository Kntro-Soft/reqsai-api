package com.kntro.reqsai.billing.interfaces.rest.mappers.response;

import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.SubscriptionResponse;

/**
 * Mapper to convert Subscription entities to REST response DTOs.
 */
public final class SubscriptionResponseMapper {

    private SubscriptionResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static SubscriptionResponse toResponse(Subscription subscription) {
        String provider = null;
        String providerExternalId = null;
        if (subscription.getProviderRef() != null) {
            if (subscription.getProviderRef().provider() != null) {
                provider = subscription.getProviderRef().provider().name();
            }
            providerExternalId = subscription.getProviderRef().externalId();
        }
        return new SubscriptionResponse(
                subscription.getId().toString(),
                subscription.getOrganizationId().toString(),
                subscription.getPlanType().name(),
                subscription.getStatus().name(),
                provider,
                providerExternalId,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getTokenQuotaUsed(),
                subscription.getCancelledAt()
        );
    }
}
