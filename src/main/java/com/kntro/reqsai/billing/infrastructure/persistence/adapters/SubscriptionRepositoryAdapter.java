package com.kntro.reqsai.billing.infrastructure.persistence.adapters;

import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.infrastructure.persistence.repositories.SubscriptionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementing the {@link SubscriptionRepositoryPort} using a Spring Data JPA repository.
 */
@Repository
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements SubscriptionRepositoryPort {

    private final SubscriptionJpaRepository jpa;

    @Override
    public Subscription save(Subscription subscription) {
        return jpa.save(subscription);
    }

    @Override
    public Optional<Subscription> findByOrganizationId(UUID organizationId) {
        return jpa.findByOrganizationId(organizationId);
    }

    @Override
    public boolean existsByOrganizationId(UUID organizationId) {
        return jpa.existsByOrganizationId(organizationId);
    }
}
