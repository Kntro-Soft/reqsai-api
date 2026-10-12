package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.codebase.api.CodeFootprint;
import com.kntro.reqsai.codebase.api.CodebaseModuleApi;
import com.kntro.reqsai.discovery.insights.DiscoveryInsightsPort;
import com.kntro.reqsai.discovery.insights.ProjectActivity;
import com.kntro.reqsai.overview.application.result.OrganizationOverview;
import com.kntro.reqsai.overview.application.result.ProjectActivitySummary;
import com.kntro.reqsai.overview.domain.exception.OverviewExceptions;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.insights.MemberHeadcount;
import com.kntro.reqsai.workspace.insights.ProjectRef;
import com.kntro.reqsai.workspace.insights.WorkspaceInsightsPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the overview of an organization over the projects the caller may see:
 * an owner or admin gets the whole organization, a regular member the projects
 * they reach. Only aggregates leave here (no story titles), so the page needs
 * nothing beyond belonging to the organization. The people count is for owners
 * and admins alone.
 */
@Service
public class OrganizationOverviewService {

    private static final int TOP_PROJECTS = 5;

    private final WorkspaceInsightsPort workspaceInsights;
    private final DiscoveryInsightsPort discovery;
    private final CodebaseModuleApi code;
    private final Clock clock;

    @Autowired
    public OrganizationOverviewService(WorkspaceInsightsPort workspaceInsights, DiscoveryInsightsPort discovery,
            CodebaseModuleApi code) {
        this(workspaceInsights, discovery, code, Clock.systemUTC());
    }

    OrganizationOverviewService(WorkspaceInsightsPort workspaceInsights, DiscoveryInsightsPort discovery,
            CodebaseModuleApi code, Clock clock) {
        this.workspaceInsights = workspaceInsights;
        this.discovery = discovery;
        this.code = code;
        this.clock = clock;
    }

    /**
     * @throws com.kntro.reqsai.shared.domain.exception.DomainException
     *             when the request's tenant is not the organization asked for
     */
    public OrganizationOverview overview(UUID organizationId, UUID callerId) {
        // The figures come from the schema of the tenant bound to the request; never
        // answer for another one.
        if (!organizationId.toString().equals(TenantContext.getCurrentTenant())) {
            throw OverviewExceptions.organizationMismatch(organizationId);
        }
        List<ProjectRef> projects = workspaceInsights.accessibleProjects(organizationId, callerId);
        List<UUID> ids = projects.stream().map(ProjectRef::id).toList();

        Instant now = clock.instant();
        List<LocalDate> window = Weeks.window(now, ProjectOverviewService.WEEKS);
        Instant since = Weeks.start(window);

        MemberHeadcount members = workspaceInsights.memberHeadcount(organizationId, callerId).orElse(null);
        CodeFootprint footprint = ids.isEmpty() ? CodeFootprint.none() : code.footprint(ids);
        return new OrganizationOverview(now, ProjectOverviewService.WEEKS, projects.size(), members,
                OverviewComposer.backlog(discovery.backlog(ids, since, 0), window),
                OverviewComposer.discovery(discovery.sessions(ids, since, 0), window),
                topProjects(projects, discovery.activityByProject(ids)), footprint);
    }

    private static List<ProjectActivitySummary> topProjects(List<ProjectRef> projects, List<ProjectActivity> activity) {
        Map<UUID, ProjectRef> byId = projects.stream().collect(Collectors.toMap(ProjectRef::id, Function.identity()));
        Comparator<ProjectActivitySummary> busiest = Comparator
                .comparingLong((ProjectActivitySummary p) -> p.stories() + p.sessions()).reversed()
                .thenComparing(ProjectActivitySummary::lastActivityAt,
                        Comparator.nullsLast(Comparator.<Instant>naturalOrder().reversed()))
                .thenComparing(ProjectActivitySummary::name, String.CASE_INSENSITIVE_ORDER);
        return activity.stream().filter(a -> byId.containsKey(a.projectId())).map(a -> {
            ProjectRef project = byId.get(a.projectId());
            return new ProjectActivitySummary(a.projectId(), project.name(), project.demo(), a.stories(), a.sessions(),
                    a.lastActivityAt());
        }).sorted(busiest).limit(TOP_PROJECTS).toList();
    }
}
