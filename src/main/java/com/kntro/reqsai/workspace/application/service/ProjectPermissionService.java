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

import java.util.UUID;

/**
 * Resolves the caller's effective rights to manage a project's members and roles, per request.
 * <p>
 * Authorization is layered: org {@code OWNER}/{@code ADMIN} always pass (they implicitly manage every
 * project). Otherwise the caller must be an active org member assigned to the project, and the
 * {@link ProjectRole} backing that assignment must carry the required {@link Permission}. Per-project
 * permissions are always queried from the database here — they are never embedded in the JWT.
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
     * otherwise the caller needs a {@link ProjectMember} assignment whose {@link ProjectRole} carries it.
     */
    public boolean hasPermission(
            Organization organization, UUID projectId, UUID requestedBy, Permission permission) {
        return orgAccess.isOwnerOrAdmin(organization, requestedBy)
                || hasProjectPermission(organization, projectId, requestedBy, permission);
    }

    private boolean hasProjectPermission(
            Organization organization, UUID projectId, UUID requestedBy, Permission permission) {
        Member member = members.findByOrganizationIdAndUserIdAndStatus(
                        organization.getId(), requestedBy, MemberStatus.ACTIVE)
                .orElse(null);
        if (member == null) {
            return false;
        }
        ProjectMember assignment = assignments.findAllByMemberId(member.getId()).stream()
                .filter(a -> a.getProjectId().equals(projectId))
                .findFirst()
                .orElse(null);
        if (assignment == null) {
            return false;
        }
        return roles.findByIdAndProjectId(assignment.getRoleId(), projectId)
                .map(ProjectRole::getPermissions)
                .map(permissions -> permissions.contains(permission))
                .orElse(false);
    }
}
