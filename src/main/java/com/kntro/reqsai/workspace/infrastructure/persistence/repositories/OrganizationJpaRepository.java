package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.Slug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link Organization}. Backs {@code OrganizationRepositoryAdapter} and
 * triggers domain-event publication on {@code save()} (via {@code @DomainEvents}).
 */
public interface OrganizationJpaRepository extends JpaRepository<Organization, UUID> {

    boolean existsBySlug(Slug slug);
}
