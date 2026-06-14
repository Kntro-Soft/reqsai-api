package com.kntro.reqsai.workspace.domain.exception;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

import java.util.UUID;

/**
 * Factory for Workspace Management domain exceptions — the context-specific counterpart of the shared
 * {@code Exceptions}. Not-found cases return {@link EntityNotFoundException}; the rest a
 * {@link DomainException} carrying a {@link WorkspaceError}.
 */
public final class WorkspaceExceptions {

    private WorkspaceExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static EntityNotFoundException organizationNotFound(UUID id) {
        return new EntityNotFoundException(WorkspaceError.ORGANIZATION_NOT_FOUND,
                "Organization not found: " + id);
    }

    public static DomainException slugAlreadyExists(String slug) {
        return new DomainException(WorkspaceError.ORGANIZATION_SLUG_ALREADY_EXISTS,
                "Slug already in use: " + slug);
    }
}
