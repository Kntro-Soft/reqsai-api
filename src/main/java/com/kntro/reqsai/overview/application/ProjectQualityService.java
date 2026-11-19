package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.discovery.insights.DiscoveryInsightsPort;
import com.kntro.reqsai.discovery.insights.QualityEvidence;
import com.kntro.reqsai.overview.application.result.QualityIndicators;
import com.kntro.reqsai.overview.domain.exception.OverviewExceptions;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import com.kntro.reqsai.workspace.insights.WorkspaceInsightsPort;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Builds the quality indicators of one project for a period (US71). They quote stories and meetings, so the caller
 * needs both {@code STORY_READ} and {@code SESSION_READ} on the project; organization owners and admins pass every
 * check. The period is a range of UTC days, both ends included; without dates it is the last 30 days up to today.
 */
@Service
public class ProjectQualityService {

    /** Days the period covers when the caller gives no dates. */
    public static final int DEFAULT_DAYS = 30;
    /** The longest period that can be asked for, in days. */
    public static final int MAX_DAYS = 366;

    private final WorkspaceModuleApi workspace;
    private final WorkspaceInsightsPort workspaceInsights;
    private final DiscoveryInsightsPort discovery;
    private final Clock clock;

    @Autowired
    public ProjectQualityService(WorkspaceModuleApi workspace, WorkspaceInsightsPort workspaceInsights,
            DiscoveryInsightsPort discovery) {
        this(workspace, workspaceInsights, discovery, Clock.systemUTC());
    }

    ProjectQualityService(WorkspaceModuleApi workspace, WorkspaceInsightsPort workspaceInsights,
            DiscoveryInsightsPort discovery, Clock clock) {
        this.workspace = workspace;
        this.workspaceInsights = workspaceInsights;
        this.discovery = discovery;
        this.clock = clock;
    }

    /**
     * @param from
     *            first day of the period, or null for 29 days before {@code to}
     * @param to
     *            last day of the period, or null for today
     * @throws com.kntro.reqsai.shared.domain.exception.DomainException
     *             when the caller lacks the permissions, the project does not exist or the period is not valid
     */
    public QualityIndicators indicators(UUID projectId, UUID callerId, @Nullable LocalDate from,
            @Nullable LocalDate to) {
        if (!(workspace.callerHasProjectPermission(projectId, callerId, "STORY_READ")
                && workspace.callerHasProjectPermission(projectId, callerId, "SESSION_READ"))) {
            throw OverviewExceptions.qualityAccessDenied(projectId, callerId);
        }
        workspaceInsights.projectKnowledge(projectId)
                .orElseThrow(() -> OverviewExceptions.projectNotFound(projectId));

        LocalDate last = to != null ? to : clock.instant().atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate first = from != null ? from : last.minusDays(DEFAULT_DAYS - 1L);
        if (first.isAfter(last)) {
            throw OverviewExceptions.invalidPeriod("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS) {
            throw OverviewExceptions.invalidPeriod("the period must not exceed " + MAX_DAYS + " days");
        }
        Instant start = first.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = last.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        QualityEvidence evidence = discovery.qualityEvidence(projectId, start, end);
        return QualityIndicatorsCalculator.calculate(first, last, evidence);
    }
}
