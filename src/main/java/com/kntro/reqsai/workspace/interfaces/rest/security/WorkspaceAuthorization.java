package com.kntro.reqsai.workspace.interfaces.rest.security;

import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.application.service.ProjectAccessService;
import com.kntro.reqsai.workspace.application.service.ProjectPermissionService;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * SpEL-facing authorization facade for the workspace REST endpoints, referenced from
 * {@code @PreAuthorize} as {@code @authz}. Each method resolves the caller from the {@link Authentication}
 * and loads the {@link Organization}, then delegates the actual policy to the application authorization
 * services ({@code OrganizationAdminAccessService}, {@code ProjectPermissionService},
 * {@code ProjectAccessService}) — the single source of truth.
 * <p>
 * Coarse resource gates (owner / owner-or-admin / member / project access / project permission) live
 * here. Fine-grained, multi-entity rules (e.g. an admin may not change another admin's role) and per-row
 * data filtering (e.g. a member only lists their assigned projects) remain in the handlers/queries, as
 * they cannot be expressed as a boolean method-security gate.
 * <p>
 * When the organization does not exist the gate passes so the handler answers 404 rather than masking it
 * as a 403.
 */
@Component("authz")
@RequiredArgsConstructor
public class WorkspaceAuthorization {

    private final OrganizationRepository organizations;
    private final OrganizationAdminAccessService orgAccess;
    private final ProjectPermissionService projectPermission;
    private final ProjectAccessService projectAccess;
    private final WorkspaceModuleApi moduleApi;

    /** Caller is the organization owner. */
    public boolean orgOwner(UUID orgId, Authentication authentication) {
        return onOrg(orgId, authentication, orgAccess::isOwner);
    }

    /** Caller is owner or admin of the organization. */
    public boolean orgOwnerOrAdmin(UUID orgId, Authentication authentication) {
        return onOrg(orgId, authentication, orgAccess::isOwnerOrAdmin);
    }

    /** Caller belongs to the organization (owner or any active member). */
    public boolean orgMember(UUID orgId, Authentication authentication) {
        return onOrg(orgId, authentication, orgAccess::isMember);
    }

    /** Caller may access the given project. */
    public boolean projectAccess(UUID orgId, UUID projectId, Authentication authentication) {
        return onOrg(orgId, authentication,
                (org, userId) -> projectAccess.canAccessProject(org, projectId, userId));
    }

    /**
     * Caller may access the given project of the <em>current tenant</em> (the JWT {@code orgId} bound by
     * the authentication filter). For routes that carry no {@code orgId} path variable, e.g.
     * {@code /api/projects/{projectId}/me/permissions}. Owner/admin bypass is identical to
     * {@link #projectAccess(UUID, UUID, Authentication)}; denies when no tenant is bound to the request.
     */
    public boolean projectAccess(UUID projectId, Authentication authentication) {
        UUID userId = callerId(authentication);
        return userId != null && moduleApi.callerCanAccessProject(projectId, userId);
    }

    /** Caller holds the named {@link Permission} on the given project. */
    public boolean projectPermission(
            UUID orgId, UUID projectId, String permission, Authentication authentication) {
        Permission required = Permission.valueOf(permission);
        return onOrg(orgId, authentication,
                (org, userId) -> projectPermission.hasPermission(org, projectId, userId, required));
    }

    /**
     * Caller holds the named {@link Permission} on the given project of the <em>current tenant</em>
     * (the JWT {@code orgId} bound by the authentication filter). For routes that carry no
     * {@code orgId} path variable, e.g. the discovery module's {@code /api/projects/{projectId}/...}.
     * Owner/admin bypass is identical to {@link #projectPermission(UUID, UUID, String, Authentication)};
     * denies when no tenant is bound to the request.
     */
    public boolean projectPermission(UUID projectId, String permission, Authentication authentication) {
        UUID userId = callerId(authentication);
        return userId != null && moduleApi.callerHasProjectPermission(projectId, userId, permission);
    }

    private boolean onOrg(UUID orgId, Authentication authentication, BiPredicate<Organization, UUID> check) {
        UUID userId = callerId(authentication);
        if (userId == null) {
            return false;
        }
        // Absent organization → let the handler produce a 404 instead of masking it as a 403.
        return organizations.findById(orgId).map(org -> check.test(org, userId)).orElse(true);
    }

    private UUID callerId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
