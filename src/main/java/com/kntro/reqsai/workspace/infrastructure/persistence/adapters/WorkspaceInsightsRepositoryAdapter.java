package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.WorkspaceInsightsRepository;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.WorkspaceInsightsJpaRepository;
import com.kntro.reqsai.workspace.insights.ProjectKnowledge;
import com.kntro.reqsai.workspace.insights.ProjectRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapts {@link WorkspaceInsightsRepository} to the counting queries of
 * {@link WorkspaceInsightsJpaRepository}.
 */
@Repository
@RequiredArgsConstructor
public class WorkspaceInsightsRepositoryAdapter implements WorkspaceInsightsRepository {

    private final WorkspaceInsightsJpaRepository jpa;

    @Override
    public List<ProjectRef> activeProjects(UUID organizationId) {
        return jpa.activeProjects(organizationId);
    }

    @Override
    public List<ProjectRef> activeProjects(UUID organizationId, Collection<UUID> projectIds) {
        return projectIds.isEmpty() ? List.of() : jpa.activeProjectsIn(organizationId, projectIds);
    }

    @Override
    public Optional<ProjectKnowledge> knowledgeOf(UUID projectId) {
        return jpa.knowledgeOf(projectId).stream().findFirst()
                .map(row -> new ProjectKnowledge(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(), ((Number) row[3]).longValue()));
    }

    @Override
    public Map<String, Map<String, Long>> memberCounts(UUID organizationId, UUID ownerId) {
        Map<String, Map<String, Long>> counts = new HashMap<>();
        for (Object[] row : jpa.memberCounts(organizationId, ownerId)) {
            counts.computeIfAbsent(String.valueOf(row[0]), role -> new HashMap<>()).put(String.valueOf(row[1]),
                    ((Number) row[2]).longValue());
        }
        return counts;
    }
}
