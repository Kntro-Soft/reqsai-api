package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.application.search.ProjectScope;
import com.kntro.reqsai.shared.application.search.SearchHit;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.WorkspaceLexicalSearchRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.search.WorkspaceSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Workspace implementation of the {@code workspace::search} named interface. Applies the same access
 * rules as the workspace query handlers (owner/admin see all projects; regular members see only their
 * assigned ones) and scopes org/member searches to the caller's own organizations, so global search
 * never leaks unauthorized rows.
 */
@Component
@RequiredArgsConstructor
class WorkspaceSearchPortImpl implements WorkspaceSearchPort {

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final ProjectAccessService projectAccess;
    private final WorkspaceLexicalSearchRepository lexicalSearch;

    @Override
    @Transactional(readOnly = true)
    public ProjectScope resolveProjectScope(UUID organizationId, UUID callerId) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(organizationId));
        Optional<Set<UUID>> accessible = projectAccess.accessibleProjectIds(organization, callerId);
        return accessible.map(ProjectScope::restrictedTo).orElseGet(ProjectScope::unrestricted);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> searchProjects(String term, int limit, UUID organizationId, ProjectScope scope) {
        if (scope.isEmpty()) {
            return List.of();
        }
        if (scope.all()) {
            return lexicalSearch.searchProjectsInOrganization(organizationId, term, limit);
        }
        return lexicalSearch.searchProjectsInIds(organizationId, scope.projectIds(), term, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> searchOrganizations(String term, int limit, UUID callerId) {
        Set<UUID> accessibleOrgIds = accessibleOrganizationIds(callerId);
        if (accessibleOrgIds.isEmpty()) {
            return List.of();
        }
        return lexicalSearch.searchOrganizations(accessibleOrgIds, term, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> searchMembers(String term, int limit, UUID organizationId, UUID callerId) {
        // Only members of the organization may search its member directory.
        if (!accessibleOrganizationIds(callerId).contains(organizationId)) {
            return List.of();
        }
        return lexicalSearch.searchMembers(organizationId, term, limit);
    }

    /** Organizations the caller owns or is an active member of. */
    private Set<UUID> accessibleOrganizationIds(UUID callerId) {
        Stream<UUID> owned = organizations.findAllByOwnerId(callerId).stream().map(Organization::getId);
        Stream<UUID> memberOf = members.findAllByUserIdAndStatus(callerId, MemberStatus.ACTIVE).stream()
                .map(member -> member.getOrganizationId());
        return Stream.concat(owned, memberOf).collect(Collectors.toUnmodifiableSet());
    }
}
