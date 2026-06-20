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

    public static DomainException organizationEditPermissionDenied(UUID organizationId, UUID userId) {
        return new DomainException(WorkspaceError.ORGANIZATION_EDIT_PERMISSION_DENIED,
                "User %s cannot edit organization %s".formatted(userId, organizationId));
    }

    public static EntityNotFoundException glossaryNotFound(UUID projectId) {
        return new EntityNotFoundException(WorkspaceError.GLOSSARY_NOT_FOUND,
                "Glossary not found for project: " + projectId);
    }

    public static DomainException glossaryTermAlreadyExists(String term) {
        return new DomainException(WorkspaceError.GLOSSARY_TERM_ALREADY_EXISTS,
                "Glossary term already exists in this project: " + term);
    }

    public static EntityNotFoundException glossaryTermNotFound(UUID termId) {
        return new EntityNotFoundException(WorkspaceError.GLOSSARY_TERM_NOT_FOUND,
                "Glossary term not found: " + termId);
    }

    public static DomainException glossaryTermPlanLimitExceeded(int maxTerms) {
        return new DomainException(WorkspaceError.GLOSSARY_TERM_PLAN_LIMIT_EXCEEDED,
                "Glossary term limit reached for this plan: " + maxTerms);
    }

    public static EntityNotFoundException projectNotFound(UUID id) {
        return new EntityNotFoundException(WorkspaceError.PROJECT_NOT_FOUND,
                "Project not found: " + id);
    }

    public static DomainException projectNameAlreadyExists(String name) {
        return new DomainException(WorkspaceError.PROJECT_NAME_ALREADY_EXISTS,
                "Project name already exists in this organization: " + name);
    }

    public static DomainException projectPlanLimitExceeded(int maxProjects) {
        return new DomainException(WorkspaceError.PROJECT_PLAN_LIMIT_EXCEEDED,
                "Project limit reached for this plan: " + maxProjects);
    }

    public static DomainException projectConstraintAlreadyExists(String description) {
        return new DomainException(WorkspaceError.PROJECT_CONSTRAINT_ALREADY_EXISTS,
                "Project constraint already exists in this project: " + description);
    }

    public static EntityNotFoundException projectConstraintNotFound(UUID constraintId) {
        return new EntityNotFoundException(WorkspaceError.PROJECT_CONSTRAINT_NOT_FOUND,
                "Project constraint not found: " + constraintId);
    }

    public static EntityNotFoundException projectDocumentNotFound(UUID documentId) {
        return new EntityNotFoundException(WorkspaceError.PROJECT_DOCUMENT_NOT_FOUND,
                "Project document not found: " + documentId);
    }

    public static DomainException projectDocumentAlreadyExists(String name) {
        return new DomainException(WorkspaceError.PROJECT_DOCUMENT_ALREADY_EXISTS,
                "Project document already exists in this project: " + name);
    }

    public static DomainException projectDocumentPlanLimitExceeded(int maxDocuments) {
        return new DomainException(WorkspaceError.PROJECT_DOCUMENT_PLAN_LIMIT_EXCEEDED,
                "Project document limit reached for this plan: " + maxDocuments);
    }
}
