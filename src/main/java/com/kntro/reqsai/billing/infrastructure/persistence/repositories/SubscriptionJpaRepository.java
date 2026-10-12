package com.kntro.reqsai.billing.infrastructure.persistence.repositories;

import com.kntro.reqsai.billing.domain.model.Subscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository interface for Subscription entities.
 * Kept package-private to enforce encapsulation.
 */
public interface SubscriptionJpaRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByOrganizationId(UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.organizationId = :organizationId")
    Optional<Subscription> findLockedByOrganizationId(UUID organizationId);

    Optional<Subscription> findByProviderRefExternalId(String externalId);

    boolean existsByOrganizationId(UUID organizationId);
}
