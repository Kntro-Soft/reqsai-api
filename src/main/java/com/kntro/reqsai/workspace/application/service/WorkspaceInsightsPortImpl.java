package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.WorkspaceInsightsRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.insights.MemberHeadcount;
import com.kntro.reqsai.workspace.insights.ProjectKnowledge;
import com.kntro.reqsai.workspace.insights.ProjectRef;
import com.kntro.reqsai.workspace.insights.WorkspaceInsightsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Workspace implementation of the {@code workspace::insights} named interface.
 * Project visibility reuses {@link ProjectAccessService}, the single source of
 * truth also behind the project list and the global search.
 */
@Component
@RequiredArgsConstructor
class WorkspaceInsightsPortImpl implements WorkspaceInsightsPort {

    private final OrganizationRepository organizations;
    private final ProjectAccessService projectAccess;
    private final OrganizationAdminAccessService orgAccess;
    private final WorkspaceInsightsRepository insights;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectRef> accessibleProjects(UUID organizationId, UUID callerId) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(organizationId));
        Optional<Set<UUID>> accessible = projectAccess.accessibleProjectIds(organization, callerId);
        return accessible.isEmpty()
                ? insights.activeProjects(organizationId)
                : insights.activeProjects(organizationId, accessible.get());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectKnowledge> projectKnowledge(UUID projectId) {
        return insights.knowledgeOf(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberHeadcount> memberHeadcount(UUID organizationId, UUID callerId) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(organizationId));
        if (!orgAccess.isOwnerOrAdmin(organization, callerId)) {
            return Optional.empty();
        }
        Map<String, Map<String, Long>> counts = insights.memberCounts(organizationId, organization.getOwnerId());

        Map<String, Long> activeByRole = new LinkedHashMap<>();
        for (OrgRole role : OrgRole.values()) {
            activeByRole.put(role.name(),
                    counts.getOrDefault(role.name(), Map.of()).getOrDefault(MemberStatus.ACTIVE.name(), 0L));
        }
        activeByRole.merge(OrgRole.OWNER.name(), 1L, Long::sum);
        long pending = counts.values().stream()
                .mapToLong(byStatus -> byStatus.getOrDefault(MemberStatus.PENDING.name(), 0L)).sum();
        return Optional.of(new MemberHeadcount(activeByRole.values().stream().mapToLong(Long::longValue).sum(), pending,
                activeByRole));
    }
}
