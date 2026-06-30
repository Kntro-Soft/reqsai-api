package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves which projects a caller may see/access within an organization.
 * <p>
 * Org {@code OWNER} and {@code ADMIN} implicitly have access to <em>all</em> projects in their
 * organization — no explicit {@link ProjectMember} row is required. A regular {@code MEMBER} can
 * access only the projects where they hold an explicit {@code ProjectMember} assignment.
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessService {

    private final MemberRepository members;
    private final ProjectMemberRepository assignments;
    private final OrganizationAdminAccessService orgAccess;

    /**
     * The set of project ids the caller may access, or {@link Optional#empty()} when the caller is an
     * org owner/admin and therefore has unrestricted access to every project in the organization.
     * Throws when the caller is neither owner/admin nor an active member of the organization.
     */
    public Optional<Set<UUID>> accessibleProjectIds(Organization organization, UUID requestedBy) {
        if (orgAccess.isOwnerOrAdmin(organization, requestedBy)) {
            return Optional.empty();
        }
        Member member = members.findByOrganizationIdAndUserIdAndStatus(
                        organization.getId(), requestedBy, MemberStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.insufficientPermissions(
                        "access projects in organization " + organization.getId(), requestedBy));

        Set<UUID> projectIds = assignments.findAllByMemberId(member.getId()).stream()
                .map(ProjectMember::getProjectId)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(projectIds);
    }

    /**
     * Asserts the caller may access the given project: owners/admins always pass; a regular member must
     * hold an explicit assignment for that project. Throws otherwise.
     */
    public void assertCanAccessProject(Organization organization, UUID projectId, UUID requestedBy) {
        Optional<Set<UUID>> accessible = accessibleProjectIds(organization, requestedBy);
        if (accessible.isPresent() && !accessible.get().contains(projectId)) {
            throw WorkspaceExceptions.insufficientPermissions("access project " + projectId, requestedBy);
        }
    }
}
