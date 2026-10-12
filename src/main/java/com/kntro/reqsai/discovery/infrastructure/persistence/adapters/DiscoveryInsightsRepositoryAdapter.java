package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.DiscoveryInsightsRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.DiscoveryInsightsJpaRepository;
import com.kntro.reqsai.discovery.insights.ProjectActivity;
import com.kntro.reqsai.discovery.insights.RecentSession;
import com.kntro.reqsai.discovery.insights.RecentStory;
import com.kntro.reqsai.discovery.insights.WeeklyCount;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Maps the native aggregate rows of {@link DiscoveryInsightsJpaRepository} to
 * the port's value types.
 */
@Repository
@RequiredArgsConstructor
public class DiscoveryInsightsRepositoryAdapter implements DiscoveryInsightsRepository {

    private final DiscoveryInsightsJpaRepository jpa;

    @Override
    public Map<StoryStatus, Long> storiesByStatus(Collection<UUID> projectIds) {
        return counts(jpa.storiesByStatus(projectIds), StoryStatus.class, StoryStatus::valueOf);
    }

    @Override
    public Map<Priority, Long> storiesByPriority(Collection<UUID> projectIds) {
        return counts(jpa.storiesByPriority(projectIds), Priority.class, Priority::valueOf);
    }

    @Override
    public StoryTotals storyTotals(Collection<UUID> projectIds) {
        Object[] row = jpa.storyTotals(projectIds).getFirst();
        return new StoryTotals(num(row[0]), num(row[1]), num(row[2]), num(row[3]), num(row[4]), num(row[5]),
                num(row[6]));
    }

    @Override
    public List<WeeklyCount> storiesCreatedByWeek(Collection<UUID> projectIds, Instant since) {
        return weeks(jpa.storiesByWeek(projectIds, since));
    }

    @Override
    public List<RecentStory> recentStories(Collection<UUID> projectIds, int limit) {
        return jpa.recentStories(projectIds, PageRequest.of(0, limit)).stream().map(row -> new RecentStory(uuid(row[0]),
                uuid(row[1]), (String) row[2], (String) row[3], (String) row[4], instant(row[5]))).toList();
    }

    @Override
    public Map<SessionStatus, Long> sessionsByStatus(Collection<UUID> projectIds) {
        return counts(jpa.sessionsByStatus(projectIds), SessionStatus.class, SessionStatus::valueOf);
    }

    @Override
    public List<WeeklyCount> sessionsCreatedByWeek(Collection<UUID> projectIds, Instant since) {
        return weeks(jpa.sessionsByWeek(projectIds, since));
    }

    @Override
    public List<RecentSession> recentSessions(Collection<UUID> projectIds, int limit) {
        return jpa.recentSessions(projectIds, PageRequest.of(0, limit)).stream().map(
                row -> new RecentSession(uuid(row[0]), uuid(row[1]), (String) row[2], (String) row[3], instant(row[4])))
                .toList();
    }

    @Override
    public SuggestionTotals suggestionTotals(Collection<UUID> projectIds) {
        Object[] row = jpa.suggestionTotals(projectIds).getFirst();
        return new SuggestionTotals(num(row[0]), num(row[1]), num(row[2]), num(row[3]));
    }

    @Override
    public List<ProjectActivity> activityByProject(Collection<UUID> projectIds) {
        Map<UUID, long[]> counts = new HashMap<>();
        Map<UUID, Instant> latest = new HashMap<>();
        for (Object[] row : jpa.storyActivity(projectIds)) {
            UUID id = uuid(row[0]);
            counts.computeIfAbsent(id, k -> new long[2])[0] = num(row[1]);
            latest.merge(id, instant(row[2]), (a, b) -> a.isAfter(b) ? a : b);
        }
        for (Object[] row : jpa.sessionActivity(projectIds)) {
            UUID id = uuid(row[0]);
            counts.computeIfAbsent(id, k -> new long[2])[1] = num(row[1]);
            latest.merge(id, instant(row[2]), (a, b) -> a.isAfter(b) ? a : b);
        }
        return counts.entrySet().stream()
                .map(e -> new ProjectActivity(e.getKey(), e.getValue()[0], e.getValue()[1], latest.get(e.getKey())))
                .sorted(Comparator.comparing(ProjectActivity::projectId)).toList();
    }

    private static <E extends Enum<E>> Map<E, Long> counts(List<Object[]> rows, Class<E> type,
            Function<String, E> parse) {
        Map<E, Long> result = new EnumMap<>(type);
        for (Object[] row : rows) {
            result.put(parse.apply((String) row[0]), num(row[1]));
        }
        return result;
    }

    private static List<WeeklyCount> weeks(List<Object[]> rows) {
        return rows.stream().map(row -> new WeeklyCount(LocalDate.parse((String) row[0]), num(row[1]))).toList();
    }

    private static long num(@Nullable Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static Instant instant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case OffsetDateTime offset -> offset.toInstant();
            case Timestamp timestamp -> timestamp.toInstant();
            default -> throw new IllegalStateException("Unexpected timestamp type " + value.getClass());
        };
    }
}
