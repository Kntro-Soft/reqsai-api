package com.kntro.reqsai.billing.infrastructure.persistence.repositories;

import com.kntro.reqsai.billing.domain.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository interface for Subscription entities.
 * Kept package-private to enforce encapsulation.
 */
public interface SubscriptionJpaRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByOrganizationId(UUID organizationId);

    boolean existsByOrganizationId(UUID organizationId);
}
