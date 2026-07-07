package com.kntro.reqsai.billing.application.port;

import com.kntro.reqsai.billing.domain.model.Subscription;

import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for managing Subscription persistence.
 */
public interface SubscriptionRepositoryPort {

    Subscription save(Subscription subscription);

    Optional<Subscription> findByOrganizationId(UUID organizationId);

    Optional<Subscription> findByProviderExternalId(String externalId);

    boolean existsByOrganizationId(UUID organizationId);
}
