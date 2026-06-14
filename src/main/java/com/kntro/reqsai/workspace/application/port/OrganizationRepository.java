package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.Slug;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link Organization} aggregate (global {@code public.organizations} registry).
 * Implemented by an adapter in {@code infrastructure}; the application layer depends only on this.
 */
public interface OrganizationRepository {

    Organization save(Organization organization);

    boolean existsBySlug(Slug slug);

    Optional<Organization> findById(UUID id);
}
