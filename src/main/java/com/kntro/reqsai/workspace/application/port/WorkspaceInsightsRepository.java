package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.insights.ProjectKnowledge;
import com.kntro.reqsai.workspace.insights.ProjectRef;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only counts behind the workspace dashboards. Each method is a single SQL
 * statement.
 */
public interface WorkspaceInsightsRepository {

    /** Active projects of the organization by name. */
    List<ProjectRef> activeProjects(UUID organizationId);

    /** Active projects of the organization among {@code projectIds}, by name. */
    List<ProjectRef> activeProjects(UUID organizationId, Collection<UUID> projectIds);

    /**
     * Glossary, constraint, document and assignment counts of the project; empty
     * when it does not exist.
     */
    Optional<ProjectKnowledge> knowledgeOf(UUID projectId);

    /**
     * Members of the organization other than its owner, who is synthesized
     * elsewhere, by role and status ({@code ACTIVE} or {@code PENDING} only):
     * {@code role -> status -> count}.
     */
    Map<String, Map<String, Long>> memberCounts(UUID organizationId, UUID ownerId);
}
