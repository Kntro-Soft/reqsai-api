package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.Slug;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.OrganizationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link OrganizationRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class OrganizationRepositoryAdapter implements OrganizationRepository {

    private final OrganizationJpaRepository jpa;

    @Override
    public Organization save(Organization organization) {
        return jpa.save(organization);
    }

    @Override
    public boolean existsBySlug(Slug slug) {
        return jpa.existsBySlug(slug);
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return jpa.findById(id);
    }
}
