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

    public static DomainException insufficientPermissions(String action, UUID userId) {
        return new DomainException(WorkspaceError.INSUFFICIENT_PERMISSIONS,
                "User %s does not have permissions to %s".formatted(userId, action));
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

    public static EntityNotFoundException memberNotFound(UUID memberId) {
        return new EntityNotFoundException(WorkspaceError.MEMBER_NOT_FOUND,
                "Member not found: " + memberId);
    }

    public static DomainException memberAlreadyExists(String identity) {
        return new DomainException(WorkspaceError.MEMBER_ALREADY_EXISTS,
                "Member already exists in this organization: " + identity);
    }

    public static DomainException memberPlanLimitExceeded(int maxMembers) {
        return new DomainException(WorkspaceError.MEMBER_PLAN_LIMIT_EXCEEDED,
                "Member limit reached for this plan: " + maxMembers);
    }

    public static EntityNotFoundException projectRoleNotFound(UUID roleId) {
        return new EntityNotFoundException(WorkspaceError.PROJECT_ROLE_NOT_FOUND,
                "Project role not found: " + roleId);
    }

    public static DomainException projectRoleInUse(UUID roleId, long assignedMembers) {
        return new DomainException(WorkspaceError.PROJECT_ROLE_IN_USE,
                "Project role %s is assigned to %d member(s); reassign them before deleting it"
                        .formatted(roleId, assignedMembers));
    }

    public static DomainException projectRoleNameAlreadyExists(String name) {
        return new DomainException(WorkspaceError.PROJECT_ROLE_NAME_ALREADY_EXISTS,
                "Project role name already exists in this project: " + name);
    }

    public static EntityNotFoundException projectMemberNotFound(UUID assignmentId) {
        return new EntityNotFoundException(WorkspaceError.PROJECT_MEMBER_NOT_FOUND,
                "Project member assignment not found: " + assignmentId);
    }

    public static DomainException projectMemberAlreadyExists(UUID memberId) {
        return new DomainException(WorkspaceError.PROJECT_MEMBER_ALREADY_EXISTS,
                "Member is already assigned to this project: " + memberId);
    }

    public static DomainException ownerCannotLeave(UUID organizationId) {
        return new DomainException(WorkspaceError.ORGANIZATION_OWNER_CANNOT_LEAVE,
                "The organization owner cannot leave organization %s — transfer ownership first".formatted(organizationId));
    }

    public static DomainException invalidOwnershipTransfer(String reason) {
        return new DomainException(WorkspaceError.INVALID_OWNERSHIP_TRANSFER,
                "Invalid ownership transfer: " + reason);
    }

    public static EntityNotFoundException invitationNotFound() {
        return new EntityNotFoundException(WorkspaceError.INVITATION_NOT_FOUND, "Invitation not found");
    }

    public static DomainException invitationExpired() {
        return new DomainException(WorkspaceError.INVITATION_EXPIRED, "Invitation has expired");
    }

    public static DomainException invitationEmailMismatch() {
        return new DomainException(WorkspaceError.INVITATION_EMAIL_MISMATCH,
                "This invitation was addressed to a different email");
    }

    public static DomainException memberNotPending(UUID memberId) {
        return new DomainException(WorkspaceError.MEMBER_NOT_PENDING,
                "Member is not pending an invitation: " + memberId);
    }
}
