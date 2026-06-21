package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.application.port.OrganizationLookupPort;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.OrganizationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Adapts the IAM {@link OrganizationLookupPort} to the workspace JPA repository. */
@Repository
@RequiredArgsConstructor
public class OrganizationLookupAdapter implements OrganizationLookupPort {

    private final OrganizationJpaRepository jpa;

    @Override
    public Optional<UUID> findOrganizationIdByOwnerId(UUID ownerId) {
        return jpa.findFirstByOwnerIdOrderByCreatedAtDesc(ownerId).map(org -> org.getId());
    }

    @Override
    public boolean isOwnerOf(UUID organizationId, UUID ownerId) {
        return jpa.existsByIdAndOwnerId(organizationId, ownerId);
    }
}
