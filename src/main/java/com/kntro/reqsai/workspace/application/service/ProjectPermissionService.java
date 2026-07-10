package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the caller's effective rights to manage a project's members and roles, per request.
 * <p>
 * Authorization is layered: org {@code OWNER}/{@code ADMIN} always pass (they implicitly manage every
 * project). Otherwise the caller's rights are the union of the organization's base-permission floor
 * (a read-only baseline every active member gets, GitHub-style) and whatever the {@link ProjectRole}
 * backing their project assignment carries. Per-project permissions are always queried from the
 * database here — they are never embedded in the JWT.
 */
@Component
@RequiredArgsConstructor
public class ProjectPermissionService {

    private final MemberRepository members;
    private final ProjectMemberRepository assignments;
    private final ProjectRoleRepository roles;
    private final OrganizationAdminAccessService orgAccess;

    public void assertHasProjectPermission(
            Organization organization, UUID projectId, UUID requestedBy, Permission permission, String action) {
        if (!hasPermission(organization, projectId, requestedBy, permission)) {
            throw WorkspaceExceptions.insufficientPermissions(action, requestedBy);
        }
    }

    /**
     * Whether the caller may exercise {@code permission} on the project: org owners/admins always may;
     * otherwise the caller may if the organization's base-permission floor grants it or a
     * {@link ProjectMember} assignment's {@link ProjectRole} carries it.
     */
    public boolean hasPermission(
            Organization organization, UUID projectId, UUID requestedBy, Permission permission) {
        return orgAccess.isOwnerOrAdmin(organization, requestedBy)
                || grantedByBasePermission(organization, requestedBy, permission)
                || hasProjectPermission(organization, projectId, requestedBy, permission);
    }

    /**
     * The caller's effective set of project permissions: all permissions for org owners/admins;
     * otherwise the union of the organization's base-permission floor and their project role's grants.
     * An empty set when the caller is neither an owner/admin nor an active member of the organization.
     */
    public Set<Permission> effectivePermissions(Organization organization, UUID projectId, UUID requestedBy) {
        if (orgAccess.isOwnerOrAdmin(organization, requestedBy)) {
            return EnumSet.allOf(Permission.class);
        }
        Member member = activeMember(organization, requestedBy);
        if (member == null) {
            return Set.of();
        }
        Set<Permission> effective = EnumSet.noneOf(Permission.class);
        effective.addAll(organization.getMemberBasePermission().grantedPermissions());
        effective.addAll(projectRolePermissions(member, projectId));
        return effective;
    }

    /** Whether the org's base floor grants {@code permission} to this active member of the org. */
    private boolean grantedByBasePermission(
            Organization organization, UUID requestedBy, Permission permission) {
        return organization.getMemberBasePermission().grantedPermissions().contains(permission)
                && activeMember(organization, requestedBy) != null;
    }

    private boolean hasProjectPermission(
            Organization organization, UUID projectId, UUID requestedBy, Permission permission) {
        Member member = activeMember(organization, requestedBy);
        if (member == null) {
            return false;
        }
        return projectRolePermissions(member, projectId).contains(permission);
    }

    /** The permissions carried by the member's project role for {@code projectId}, empty when unassigned. */
    private Set<Permission> projectRolePermissions(Member member, UUID projectId) {
        ProjectMember assignment = assignments.findAllByMemberId(member.getId()).stream()
                .filter(a -> a.getProjectId().equals(projectId))
                .findFirst()
                .orElse(null);
        if (assignment == null) {
            return Set.of();
        }
        return roles.findByIdAndProjectId(assignment.getRoleId(), projectId)
                .map(ProjectRole::getPermissions)
                .orElseGet(Set::of);
    }

    private Member activeMember(Organization organization, UUID requestedBy) {
        return members.findByOrganizationIdAndUserIdAndStatus(
                        organization.getId(), requestedBy, MemberStatus.ACTIVE)
                .orElse(null);
    }
}
