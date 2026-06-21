package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.Slug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Organization}. Backs {@code OrganizationRepositoryAdapter} and
 * triggers domain-event publication on {@code save()} (via {@code @DomainEvents}).
 */
public interface OrganizationJpaRepository extends JpaRepository<Organization, UUID> {

    boolean existsBySlug(Slug slug);

    // Returns the most-recently created org for this owner, so callers get a deterministic
    // result even when the user owns multiple organizations (LIMIT 1 avoids NonUniqueResult).
    @Query("SELECT o FROM Organization o WHERE o.ownerId = :ownerId ORDER BY o.createdAt DESC LIMIT 1")
    Optional<Organization> findFirstByOwnerIdOrderByCreatedAtDesc(@Param("ownerId") UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    List<Organization> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
