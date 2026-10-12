package com.kntro.reqsai.workspace.insights;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only figures of the Workspace context for dashboards. Runs in the
 * currently bound tenant and applies the same access rules as the workspace
 * queries, so a caller only ever learns about what they may reach.
 * <p>
 * Exposed as the {@code workspace::insights} named interface.
 */
public interface WorkspaceInsightsPort {

    /**
     * The active projects of the organization the caller may see, by name: every
     * one for an owner or admin (or when members have a base permission), the
     * assigned ones for a regular member. Throws when the caller does not belong to
     * the organization.
     */
    List<ProjectRef> accessibleProjects(UUID organizationId, UUID callerId);

    /** The knowledge a project holds, or empty when the project does not exist. */
    Optional<ProjectKnowledge> projectKnowledge(UUID projectId);

    /**
     * The people of the organization — owner, active and invited members — or empty
     * when the caller is not an owner or admin of it: the roster is not for regular
     * members.
     */
    Optional<MemberHeadcount> memberHeadcount(UUID organizationId, UUID callerId);
}
