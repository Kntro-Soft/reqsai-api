package com.kntro.reqsai.workspace.domain.model;

/**
 * Fine-grained, project-scoped capabilities a {@link ProjectRole} may grant. Modelled as
 * {@code RESOURCE_ACTION} (the {@code resource:action} pattern used by GitHub/GitLab/GCP/AWS IAM),
 * splitting read from write/manage per resource so read-only roles are expressible while keeping the
 * catalog small enough to avoid role explosion. Every value here is enforced by a controller
 * {@code @PreAuthorize("@authz.projectPermission(...)")} gate; owners/org-admins bypass these checks.
 */
public enum Permission {
    // Project settings / lifecycle
    PROJECT_UPDATE,
    PROJECT_ARCHIVE,
    PROJECT_DELETE,

    // Project members (assignments)
    MEMBER_READ,
    MEMBER_INVITE,
    MEMBER_UPDATE_ROLE,
    MEMBER_REMOVE,

    // Project roles (RBAC administration)
    ROLE_READ,
    ROLE_CREATE,
    ROLE_UPDATE,
    ROLE_DELETE,

    // Documents
    DOCUMENT_READ,
    DOCUMENT_CREATE,
    DOCUMENT_UPDATE,
    DOCUMENT_DELETE,

    // Glossary + terms
    GLOSSARY_READ,
    GLOSSARY_TERM_WRITE,
    GLOSSARY_TERM_DELETE,

    // Constraints
    CONSTRAINT_READ,
    CONSTRAINT_WRITE,

    // Discovery sessions (elicitation lifecycle — enforced by the discovery context via @authz).
    // Sessions are permanent immutable history and can never be deleted, so there is no SESSION_DELETE.
    SESSION_READ,
    SESSION_RUN,
    SESSION_DECIDE,

    // User stories (backlog)
    STORY_READ,
    STORY_WRITE,

    // Third-party integrations (e.g. Jira). Org-level connection administration is gated by the
    // org owner/admin check; these project-scoped permissions gate the per-project target + push.
    INTEGRATION_READ,
    INTEGRATION_WRITE,
    INTEGRATION_DELETE,
    INTEGRATION_SYNC
}
