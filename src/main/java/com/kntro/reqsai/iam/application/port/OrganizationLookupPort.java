package com.kntro.reqsai.iam.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for resolving organization identity from within the IAM context.
 * Implemented in {@code workspace.infrastructure} so IAM does not depend on workspace internals.
 */
public interface OrganizationLookupPort {
    /**
     * Default active organization for a user: the most recently created org they own, or — if they own
     * none — the first org where they are an active member. Empty when the user belongs to no org.
     */
    Optional<UUID> findDefaultOrganizationId(UUID userId);

    /** Returns {@code true} when the user owns {@code organizationId} or is an active member of it. */
    boolean canAccess(UUID organizationId, UUID userId);
}
