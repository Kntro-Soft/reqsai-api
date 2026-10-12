package com.kntro.reqsai.overview.interfaces.rest.mappers.response;

import com.kntro.reqsai.codebase.api.CodeFootprint;
import com.kntro.reqsai.overview.application.result.BacklogSummary;
import com.kntro.reqsai.overview.application.result.DiscoverySummary;
import com.kntro.reqsai.overview.application.result.KnowledgeSummary;
import com.kntro.reqsai.overview.application.result.OrganizationOverview;
import com.kntro.reqsai.overview.application.result.ProjectOverview;
import com.kntro.reqsai.overview.application.result.WeekCount;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.BacklogOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.CodeOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.DiscoveryOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.KnowledgeOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.OrganizationOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.ProjectOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.SessionSummaryResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.StorySummaryResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.WeekCountResponse;

import java.util.List;

/** Maps the application's overview results to their REST responses. */
public final class OverviewResponseMapper {

    private OverviewResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ProjectOverviewResponse toResponse(ProjectOverview overview) {
        return new ProjectOverviewResponse(overview.generatedAt(), overview.weeks(),
                overview.backlog() == null ? null : toResponse(overview.backlog()),
                overview.discovery() == null ? null : toResponse(overview.discovery()),
                overview.knowledge() == null ? null : toResponse(overview.knowledge()),
                overview.code() == null ? null : toResponse(overview.code()));
    }

    public static OrganizationOverviewResponse toResponse(OrganizationOverview overview) {
        return new OrganizationOverviewResponse(overview.generatedAt(), overview.weeks(), overview.projects(),
                overview.members() == null
                        ? null
                        : new OrganizationOverviewResponse.MembersResponse(overview.members().active(),
                                overview.members().pending(), overview.members().activeByRole()),
                toResponse(overview.backlog()), toResponse(overview
                        .discovery()),
                overview.topProjects().stream()
                        .map(p -> new OrganizationOverviewResponse.TopProjectResponse(p.projectId(), p.name(), p.demo(),
                                p.stories(), p.sessions(), p.lastActivityAt()))
                        .toList(),
                toResponse(overview.code()));
    }

    private static BacklogOverviewResponse toResponse(BacklogSummary backlog) {
        return new BacklogOverviewResponse(backlog.total(), backlog.byStatus(), backlog.byPriority(),
                new BacklogOverviewResponse.StoryPointsResponse(backlog.storyPoints().total(),
                        backlog.storyPoints().approved(), backlog.storyPoints().unestimated()),
                new BacklogOverviewResponse.CoverageResponse(backlog.coverage().live(),
                        backlog.coverage().withAcceptanceCriteria(), backlog.coverage().withOrigin(),
                        backlog.coverage().linkedToCode()),
                weeks(backlog.createdByWeek()), backlog.recent().stream().map(s -> new StorySummaryResponse(s.id(),
                        s.projectId(), s.title(), s.status(), s.priority(), s.createdAt())).toList());
    }

    private static DiscoveryOverviewResponse toResponse(DiscoverySummary discovery) {
        return new DiscoveryOverviewResponse(discovery.sessions(), discovery.byStatus(),
                weeks(discovery.createdByWeek()),
                new DiscoveryOverviewResponse.AssistanceResponse(discovery.assistance().accepted(),
                        discovery.assistance().dismissed(), discovery.assistance().pending(),
                        discovery.assistance().questions(), discovery.assistance().acceptanceRate()),
                discovery.recent().stream().map(
                        s -> new SessionSummaryResponse(s.id(), s.projectId(), s.title(), s.status(), s.createdAt()))
                        .toList());
    }

    private static KnowledgeOverviewResponse toResponse(KnowledgeSummary knowledge) {
        return new KnowledgeOverviewResponse(knowledge.glossaryTerms(), knowledge.constraints(), knowledge.documents(),
                knowledge.assignedMembers());
    }

    private static CodeOverviewResponse toResponse(CodeFootprint code) {
        return new CodeOverviewResponse(code.repositories(), code.ready(), code.failed(), code.modules(), code.files(),
                code.lastIndexedAt());
    }

    private static List<WeekCountResponse> weeks(List<WeekCount> weeks) {
        return weeks.stream().map(w -> new WeekCountResponse(w.weekStart(), w.count())).toList();
    }
}
