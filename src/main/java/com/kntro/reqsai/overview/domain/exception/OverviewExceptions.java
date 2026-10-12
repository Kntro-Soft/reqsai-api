package com.kntro.reqsai.overview.domain.exception;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

import java.util.UUID;

/** Factory for the Overview context's domain exceptions. */
public final class OverviewExceptions {

    private OverviewExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static EntityNotFoundException projectNotFound(UUID projectId) {
        return new EntityNotFoundException(OverviewError.OVERVIEW_PROJECT_NOT_FOUND, "Project not found: " + projectId);
    }

    public static DomainException accessDenied(UUID projectId, UUID userId) {
        return new DomainException(OverviewError.OVERVIEW_ACCESS_DENIED,
                "User %s may not read any part of the overview of project %s".formatted(userId, projectId));
    }

    public static DomainException organizationMismatch(UUID organizationId) {
        return new DomainException(OverviewError.OVERVIEW_ORGANIZATION_MISMATCH,
                "The session is not bound to organization " + organizationId);
    }
}
