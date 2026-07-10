package com.kntro.reqsai.workspace.domain.model;

import java.util.Set;

/**
 * Organization-wide RBAC floor (GitHub "base permissions" model): a baseline applied to every project
 * member <em>in addition</em> to whatever their explicit {@link ProjectRole} grants. Project roles are
 * additive on top of this floor; organization owners/admins bypass it entirely and keep full access.
 * <ul>
 *   <li>{@code NONE} — members get nothing but their explicit project role.</li>
 *   <li>{@code READ} — members get a read-only baseline across the workspace resources.</li>
 * </ul>
 */
public enum BasePermission {

    NONE {
        @Override
        public Set<Permission> grantedPermissions() {
            return Set.of();
        }
    },

    READ {
        @Override
        public Set<Permission> grantedPermissions() {
            return READ_BASELINE;
        }
    };

    /**
     * The read-only baseline: the workspace {@code *_READ} permissions members legitimately need as a
     * floor. Third-party integrations are organization-admin configuration, so integration read is
     * deliberately excluded — members do not get integration visibility from the base floor.
     */
    private static final Set<Permission> READ_BASELINE = Set.of(
            Permission.MEMBER_READ,
            Permission.ROLE_READ,
            Permission.DOCUMENT_READ,
            Permission.GLOSSARY_READ,
            Permission.CONSTRAINT_READ,
            Permission.SESSION_READ,
            Permission.STORY_READ);

    /** The permissions this floor grants to every project member of the organization. */
    public abstract Set<Permission> grantedPermissions();
}
