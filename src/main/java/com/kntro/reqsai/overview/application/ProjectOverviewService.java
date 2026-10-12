package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.codebase.api.CodeFootprint;
import com.kntro.reqsai.codebase.api.CodebaseModuleApi;
import com.kntro.reqsai.discovery.insights.DiscoveryInsightsPort;
import com.kntro.reqsai.overview.application.result.BacklogSummary;
import com.kntro.reqsai.overview.application.result.DiscoverySummary;
import com.kntro.reqsai.overview.application.result.KnowledgeSummary;
import com.kntro.reqsai.overview.application.result.ProjectOverview;
import com.kntro.reqsai.overview.domain.exception.OverviewExceptions;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import com.kntro.reqsai.workspace.insights.ProjectKnowledge;
import com.kntro.reqsai.workspace.insights.WorkspaceInsightsPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the overview of one project. Each section follows the project
 * permission that guards the same data elsewhere (stories, sessions, glossary,
 * constraints, documents, members, integrations), so the page never shows a
 * number the caller could not reach on its own page. Organization owners and
 * admins pass every check.
 */
@Service
public class ProjectOverviewService {

    /** Weeks the charts cover. */
    public static final int WEEKS = 12;
    private static final int RECENT = 5;

    private final WorkspaceModuleApi workspace;
    private final WorkspaceInsightsPort workspaceInsights;
    private final DiscoveryInsightsPort discovery;
    private final CodebaseModuleApi code;
    private final Clock clock;

    @Autowired
    public ProjectOverviewService(WorkspaceModuleApi workspace, WorkspaceInsightsPort workspaceInsights,
            DiscoveryInsightsPort discovery, CodebaseModuleApi code) {
        this(workspace, workspaceInsights, discovery, code, Clock.systemUTC());
    }

    ProjectOverviewService(WorkspaceModuleApi workspace, WorkspaceInsightsPort workspaceInsights,
            DiscoveryInsightsPort discovery, CodebaseModuleApi code, Clock clock) {
        this.workspace = workspace;
        this.workspaceInsights = workspaceInsights;
        this.discovery = discovery;
        this.code = code;
        this.clock = clock;
    }

    /**
     * @throws com.kntro.reqsai.shared.domain.exception.DomainException
     *             when the caller may read no part of the project, or the project
     *             does not exist
     */
    public ProjectOverview overview(UUID projectId, UUID callerId) {
        boolean stories = can(projectId, callerId, "STORY_READ");
        boolean sessions = can(projectId, callerId, "SESSION_READ");
        boolean glossary = can(projectId, callerId, "GLOSSARY_READ");
        boolean constraints = can(projectId, callerId, "CONSTRAINT_READ");
        boolean documents = can(projectId, callerId, "DOCUMENT_READ");
        boolean members = can(projectId, callerId, "MEMBER_READ");
        boolean integrations = can(projectId, callerId, "INTEGRATION_READ");
        if (!(stories || sessions || glossary || constraints || documents || members || integrations)) {
            throw OverviewExceptions.accessDenied(projectId, callerId);
        }
        ProjectKnowledge knowledge = workspaceInsights.projectKnowledge(projectId)
                .orElseThrow(() -> OverviewExceptions.projectNotFound(projectId));

        Instant now = clock.instant();
        List<LocalDate> window = Weeks.window(now, WEEKS);
        Instant since = Weeks.start(window);
        Set<UUID> ids = Set.of(projectId);

        BacklogSummary backlog = stories
                ? OverviewComposer.backlog(discovery.backlog(ids, since, RECENT), window)
                : null;
        DiscoverySummary sessionsSection = sessions
                ? OverviewComposer.discovery(discovery.sessions(ids, since, RECENT), window)
                : null;
        KnowledgeSummary knowledgeSection = glossary || constraints || documents || members
                ? new KnowledgeSummary(glossary ? knowledge.glossaryTerms() : null,
                        constraints ? knowledge.constraints() : null, documents ? knowledge.documents() : null,
                        members ? knowledge.assignedMembers() : null)
                : null;
        CodeFootprint codeSection = integrations ? code.footprint(ids) : null;
        return new ProjectOverview(now, WEEKS, backlog, sessionsSection, knowledgeSection, codeSection);
    }

    private boolean can(UUID projectId, UUID callerId, String permission) {
        return workspace.callerHasProjectPermission(projectId, callerId, permission);
    }
}
