package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.DiscoveryInsightsRepository;
import com.kntro.reqsai.discovery.application.port.DiscoveryInsightsRepository.StoryTotals;
import com.kntro.reqsai.discovery.application.port.DiscoveryInsightsRepository.SuggestionTotals;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.insights.BacklogInsights;
import com.kntro.reqsai.discovery.insights.DiscoveryInsightsPort;
import com.kntro.reqsai.discovery.insights.ProjectActivity;
import com.kntro.reqsai.discovery.insights.SessionInsights;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Discovery implementation of the {@code discovery::insights} named interface.
 * Turns the grouped SQL counts into statuses-as-text maps with every status
 * present (a status nobody uses reads as zero, so a chart never has a missing
 * bar), in the enums' declaration order.
 */
@Component
@RequiredArgsConstructor
class DiscoveryInsightsPortImpl implements DiscoveryInsightsPort {

    private final DiscoveryInsightsRepository insights;

    @Override
    @Transactional(readOnly = true)
    public BacklogInsights backlog(Collection<UUID> projectIds, Instant since, int recentLimit) {
        if (projectIds.isEmpty()) {
            return new BacklogInsights(0, zeros(StoryStatus.values()), zeros(Priority.values()), 0, 0, 0, 0, 0, 0, 0,
                    List.of(), List.of());
        }
        Map<String, Long> byStatus = filled(StoryStatus.values(), insights.storiesByStatus(projectIds));
        Map<String, Long> byPriority = filled(Priority.values(), insights.storiesByPriority(projectIds));
        StoryTotals totals = insights.storyTotals(projectIds);
        return new BacklogInsights(byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus, byPriority,
                totals.live(), totals.storyPoints(), totals.approvedStoryPoints(), totals.unestimated(),
                totals.withAcceptanceCriteria(), totals.withOrigin(), totals.linkedToCode(),
                insights.storiesCreatedByWeek(projectIds, since),
                recentLimit > 0 ? insights.recentStories(projectIds, recentLimit) : List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public SessionInsights sessions(Collection<UUID> projectIds, Instant since, int recentLimit) {
        if (projectIds.isEmpty()) {
            return new SessionInsights(0, zeros(SessionStatus.values()), List.of(), 0, 0, 0, 0, List.of());
        }
        Map<String, Long> byStatus = filled(SessionStatus.values(), insights.sessionsByStatus(projectIds));
        SuggestionTotals suggestions = insights.suggestionTotals(projectIds);
        return new SessionInsights(byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus,
                insights.sessionsCreatedByWeek(projectIds, since), suggestions.accepted(), suggestions.dismissed(),
                suggestions.pending(), suggestions.questions(),
                recentLimit > 0 ? insights.recentSessions(projectIds, recentLimit) : List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectActivity> activityByProject(Collection<UUID> projectIds) {
        return projectIds.isEmpty() ? List.of() : insights.activityByProject(projectIds);
    }

    private static <E extends Enum<E>> Map<String, Long> filled(E[] values, Map<E, Long> counts) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (E value : values) {
            result.put(value.name(), counts.getOrDefault(value, 0L));
        }
        return result;
    }

    private static <E extends Enum<E>> Map<String, Long> zeros(E[] values) {
        return Arrays.stream(values).collect(LinkedHashMap::new, (m, v) -> m.put(v.name(), 0L), LinkedHashMap::putAll);
    }
}
