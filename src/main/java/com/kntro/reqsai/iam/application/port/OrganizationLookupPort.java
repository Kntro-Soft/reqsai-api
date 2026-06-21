package com.kntro.reqsai.iam.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for resolving organization identity from within the IAM context.
 * Implemented in {@code workspace.infrastructure} so IAM does not depend on workspace internals.
 */
public interface OrganizationLookupPort {
    Optional<UUID> findOrganizationIdByOwnerId(UUID ownerId);

    /** Returns {@code true} when {@code organizationId} exists and is owned by {@code ownerId}. */
    boolean isOwnerOf(UUID organizationId, UUID ownerId);
}
