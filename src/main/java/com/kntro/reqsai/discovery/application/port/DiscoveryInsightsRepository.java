package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.insights.ProjectActivity;
import com.kntro.reqsai.discovery.insights.RecentSession;
import com.kntro.reqsai.discovery.insights.RecentStory;
import com.kntro.reqsai.discovery.insights.WeeklyCount;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only port for dashboard aggregates over a set of projects.
 * Tenant-scoped. Each method is one grouped SQL statement; nothing loads
 * entities. Callers never pass an empty collection (the port impl
 * short-circuits).
 */
public interface DiscoveryInsightsRepository {

    /** Story count per status; statuses with no story are absent. */
    Map<StoryStatus, Long> storiesByStatus(Collection<UUID> projectIds);

    /** Story count per priority; priorities with no story are absent. */
    Map<Priority, Long> storiesByPriority(Collection<UUID> projectIds);

    /** Points and coverage figures of the stories, one scan. */
    StoryTotals storyTotals(Collection<UUID> projectIds);

    /** Stories created per week since {@code since}, oldest week first. */
    List<WeeklyCount> storiesCreatedByWeek(Collection<UUID> projectIds, Instant since);

    /** The newest stories first. */
    List<RecentStory> recentStories(Collection<UUID> projectIds, int limit);

    /** Session count per status; statuses with no session are absent. */
    Map<SessionStatus, Long> sessionsByStatus(Collection<UUID> projectIds);

    /** Sessions created per week since {@code since}, oldest week first. */
    List<WeeklyCount> sessionsCreatedByWeek(Collection<UUID> projectIds, Instant since);

    /** The newest sessions first. */
    List<RecentSession> recentSessions(Collection<UUID> projectIds, int limit);

    /** How the analyst treated the copilot's proposals, one scan. */
    SuggestionTotals suggestionTotals(Collection<UUID> projectIds);

    /**
     * Stories, sessions and last activity per project that has any story or
     * session.
     */
    List<ProjectActivity> activityByProject(Collection<UUID> projectIds);

    /**
     * Points and coverage of the live stories ({@code DRAFT}, {@code APPROVED},
     * {@code EXPORTED}).
     */
    record StoryTotals(long live, long storyPoints, long approvedStoryPoints, long unestimated,
            long withAcceptanceCriteria, long withOrigin, long linkedToCode) {
    }

    /**
     * Story proposals by decision, and the clarifying questions raised (which are
     * answered, not accepted).
     */
    record SuggestionTotals(long accepted, long dismissed, long pending, long questions) {
    }
}
