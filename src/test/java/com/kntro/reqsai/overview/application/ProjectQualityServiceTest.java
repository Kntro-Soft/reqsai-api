package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.discovery.insights.DecidedStory;
import com.kntro.reqsai.discovery.insights.DiscoveryInsightsPort;
import com.kntro.reqsai.discovery.insights.HeldSession;
import com.kntro.reqsai.discovery.insights.QualityEvidence;
import com.kntro.reqsai.overview.application.result.QualityIndicators;
import com.kntro.reqsai.overview.domain.exception.OverviewError;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import com.kntro.reqsai.workspace.insights.ProjectKnowledge;
import com.kntro.reqsai.workspace.insights.WorkspaceInsightsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: project quality indicators")
@ExtendWith(MockitoExtension.class)
class ProjectQualityServiceTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-18T15:30:00Z");

    @Mock
    private WorkspaceModuleApi workspace;
    @Mock
    private WorkspaceInsightsPort workspaceInsights;
    @Mock
    private DiscoveryInsightsPort discovery;

    private ProjectQualityService service() {
        return new ProjectQualityService(workspace, workspaceInsights, discovery, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void permitted(boolean stories, boolean sessions) {
        when(workspace.callerHasProjectPermission(PROJECT, CALLER, "STORY_READ")).thenReturn(stories);
        if (stories) {
            when(workspace.callerHasProjectPermission(PROJECT, CALLER, "SESSION_READ")).thenReturn(sessions);
        }
    }

    private void projectExists() {
        when(workspaceInsights.projectKnowledge(PROJECT)).thenReturn(Optional.of(new ProjectKnowledge(0, 0, 0, 0)));
    }

    @Test
    @DisplayName("without dates the period is the last 30 days up to today, read as UTC days")
    void defaultsToLast30Days() {
        permitted(true, true);
        projectExists();
        when(discovery.qualityEvidence(PROJECT, Instant.parse("2026-10-20T00:00:00Z"),
                Instant.parse("2026-11-19T00:00:00Z"))).thenReturn(new QualityEvidence(List.of(), 0, List.of()));

        QualityIndicators indicators = service().indicators(PROJECT, CALLER, null, null);

        assertThat(indicators.from()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(indicators.to()).isEqualTo(LocalDate.parse("2026-11-18"));
        assertThat(indicators.hasData()).isFalse();
    }

    @Test
    @DisplayName("an explicit period includes both end days")
    void explicitPeriod() {
        permitted(true, true);
        projectExists();
        when(discovery.qualityEvidence(PROJECT, Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-04T00:00:00Z"))).thenReturn(new QualityEvidence(
                        List.of(new DecidedStory(UUID.randomUUID(), "APPROVED", "texto", "texto")), 0,
                        List.of(new HeldSession(UUID.randomUUID(), "Kickoff", "DISCOVERY",
                                Instant.parse("2026-11-02T10:00:00Z"), Instant.parse("2026-11-02T11:00:00Z")))));

        QualityIndicators indicators = service().indicators(PROJECT, CALLER, LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-03"));

        assertThat(indicators.hasData()).isTrue();
        assertThat(indicators.acceptance().rate()).isEqualTo(1.0);
        assertThat(indicators.postProcessing().medianSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("a single-day period is valid")
    void singleDay() {
        permitted(true, true);
        projectExists();
        when(discovery.qualityEvidence(PROJECT, Instant.parse("2026-11-18T00:00:00Z"),
                Instant.parse("2026-11-19T00:00:00Z"))).thenReturn(new QualityEvidence(List.of(), 0, List.of()));

        assertThat(service().indicators(PROJECT, CALLER, LocalDate.parse("2026-11-18"), LocalDate.parse("2026-11-18"))
                .hasData()).isFalse();
    }

    @Test
    @DisplayName("only to given: the 30 days ending that day")
    void onlyTo() {
        permitted(true, true);
        projectExists();
        when(discovery.qualityEvidence(PROJECT, Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-31T00:00:00Z"))).thenReturn(new QualityEvidence(List.of(), 0, List.of()));

        QualityIndicators indicators = service().indicators(PROJECT, CALLER, null, LocalDate.parse("2026-10-30"));

        assertThat(indicators.from()).isEqualTo(LocalDate.parse("2026-10-01"));
    }

    @Test
    @DisplayName("from after to is a bad request and reads nothing")
    void fromAfterTo() {
        permitted(true, true);
        projectExists();

        assertThatThrownBy(() -> service().indicators(PROJECT, CALLER, LocalDate.parse("2026-11-10"),
                LocalDate.parse("2026-11-01"))).isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(OverviewError.QUALITY_INVALID_PERIOD));
        verify(discovery, never()).qualityEvidence(any(), any(), any());
    }

    @Test
    @DisplayName("a period longer than 366 days is a bad request; exactly 366 is accepted")
    void periodTooLong() {
        permitted(true, true);
        projectExists();
        when(discovery.qualityEvidence(any(), any(), any())).thenReturn(new QualityEvidence(List.of(), 0, List.of()));

        assertThatThrownBy(() -> service().indicators(PROJECT, CALLER, LocalDate.parse("2025-11-01"),
                LocalDate.parse("2026-11-18"))).isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(OverviewError.QUALITY_INVALID_PERIOD));
        assertThat(service().indicators(PROJECT, CALLER, LocalDate.parse("2025-11-18"), LocalDate.parse("2026-11-18"))
                .hasData()).isFalse();
    }

    @Test
    @DisplayName("needs STORY_READ and SESSION_READ: lacking either is forbidden and nothing is read")
    void needsBothPermissions() {
        permitted(false, false);
        assertThatThrownBy(() -> service().indicators(PROJECT, CALLER, null, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(OverviewError.QUALITY_ACCESS_DENIED));

        permitted(true, false);
        assertThatThrownBy(() -> service().indicators(PROJECT, CALLER, null, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(OverviewError.QUALITY_ACCESS_DENIED));
        verifyNoInteractions(discovery, workspaceInsights);
    }

    @Test
    @DisplayName("a project that does not exist is not found")
    void unknownProject() {
        permitted(true, true);
        when(workspaceInsights.projectKnowledge(PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().indicators(PROJECT, CALLER, null, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(OverviewError.OVERVIEW_PROJECT_NOT_FOUND));
        verifyNoInteractions(discovery);
    }
}
