package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.DiscoveryInsightsRepository;
import com.kntro.reqsai.discovery.application.port.DiscoveryInsightsRepository.StoryTotals;
import com.kntro.reqsai.discovery.application.port.DiscoveryInsightsRepository.SuggestionTotals;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.insights.BacklogInsights;
import com.kntro.reqsai.discovery.insights.ProjectActivity;
import com.kntro.reqsai.discovery.insights.SessionInsights;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: discovery insights port")
@ExtendWith(MockitoExtension.class)
class DiscoveryInsightsPortImplTest {

    private static final Instant SINCE = Instant.parse("2026-07-20T00:00:00Z");

    @Mock
    private DiscoveryInsightsRepository insights;

    private DiscoveryInsightsPortImpl port() {
        return new DiscoveryInsightsPortImpl(insights);
    }

    @Test
    @DisplayName("backlog lists every status and priority in order, with zero for the unused ones")
    void backlogZeroFillsInEnumOrder() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        when(insights.storiesByStatus(ids)).thenReturn(Map.of(StoryStatus.DRAFT, 3L, StoryStatus.EXPORTED, 1L));
        when(insights.storiesByPriority(ids)).thenReturn(Map.of(Priority.HIGH, 4L));
        when(insights.storyTotals(ids)).thenReturn(new StoryTotals(4, 20, 5, 1, 2, 3, 0));
        when(insights.storiesCreatedByWeek(ids, SINCE)).thenReturn(List.of());
        when(insights.recentStories(ids, 5)).thenReturn(List.of());

        BacklogInsights backlog = port().backlog(ids, SINCE, 5);

        assertThat(backlog.byStatus().keySet()).containsExactly("DRAFT", "APPROVED", "REJECTED", "MERGED", "EXPORTED");
        assertThat(backlog.byStatus()).containsEntry("DRAFT", 3L).containsEntry("APPROVED", 0L)
                .containsEntry("EXPORTED", 1L);
        assertThat(backlog.byPriority().keySet()).containsExactly("LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(backlog.byPriority()).containsEntry("HIGH", 4L).containsEntry("LOW", 0L);
        assertThat(backlog.total()).isEqualTo(4);
        assertThat(backlog.storyPoints()).isEqualTo(20);
        assertThat(backlog.withAcceptanceCriteria()).isEqualTo(2);
    }

    @Test
    @DisplayName("sessions lists every status and carries the suggestion decisions")
    void sessionsZeroFills() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        when(insights.sessionsByStatus(ids)).thenReturn(Map.of(SessionStatus.COMPLETED, 2L));
        when(insights.suggestionTotals(ids)).thenReturn(new SuggestionTotals(6, 2, 1, 3));
        when(insights.sessionsCreatedByWeek(ids, SINCE)).thenReturn(List.of());
        when(insights.recentSessions(ids, 5)).thenReturn(List.of());

        SessionInsights sessions = port().sessions(ids, SINCE, 5);

        assertThat(sessions.byStatus().keySet()).containsExactly("DRAFT", "RECORDING", "PAUSED", "STOPPED",
                "PROCESSING", "COMPLETED", "FAILED");
        assertThat(sessions.total()).isEqualTo(2);
        assertThat(sessions.suggestionsAccepted()).isEqualTo(6);
        assertThat(sessions.suggestionsDismissed()).isEqualTo(2);
        assertThat(sessions.suggestionsPending()).isEqualTo(1);
        assertThat(sessions.questionsAsked()).isEqualTo(3);
    }

    @Test
    @DisplayName("a recent limit of zero skips the recent list")
    void zeroRecentLimit() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        when(insights.sessionsByStatus(ids)).thenReturn(Map.of());
        when(insights.suggestionTotals(ids)).thenReturn(new SuggestionTotals(0, 0, 0, 0));
        when(insights.sessionsCreatedByWeek(ids, SINCE)).thenReturn(List.of());

        assertThat(port().sessions(ids, SINCE, 0).recent()).isEmpty();
    }

    @Test
    @DisplayName("no projects means zeros and no query")
    void noProjects() {
        assertThat(port().backlog(Set.of(), SINCE, 5).total()).isZero();
        assertThat(port().backlog(Set.of(), SINCE, 5).byStatus()).hasSize(StoryStatus.values().length);
        assertThat(port().sessions(Set.of(), SINCE, 5).total()).isZero();
        List<ProjectActivity> activity = port().activityByProject(Set.of());
        assertThat(activity).isEmpty();
        verifyNoInteractions(insights);
    }
}
