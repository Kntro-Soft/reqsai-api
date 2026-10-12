package com.kntro.reqsai.discovery.insights;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The state of a set of projects' backlog.
 * <p>
 * A story is <em>live</em> while it still counts as work: {@code DRAFT},
 * {@code APPROVED} or {@code EXPORTED}. Rejected and merged stories are history
 * and stay out of the points and coverage figures.
 *
 * @param total
 *            every story, whatever its status
 * @param byStatus
 *            story count per status, every status present, in lifecycle order
 * @param byPriority
 *            story count per priority, every priority present, from low to
 *            critical
 * @param liveStories
 *            stories still counting as work
 * @param storyPoints
 *            points of the live stories
 * @param approvedStoryPoints
 *            points of the approved or exported stories
 * @param unestimated
 *            live stories with no points yet
 * @param withAcceptanceCriteria
 *            live stories that have at least one acceptance criterion
 * @param withOrigin
 *            live stories that quote the conversation they came from
 * @param linkedToCode
 *            live stories tied to at least one module of the client's code
 * @param createdByWeek
 *            stories created per week since the requested date (weeks without
 *            any are absent)
 * @param recent
 *            the newest stories
 */
public record BacklogInsights(long total, Map<String, Long> byStatus, Map<String, Long> byPriority, long liveStories,
        long storyPoints, long approvedStoryPoints, long unestimated, long withAcceptanceCriteria, long withOrigin,
        long linkedToCode, List<WeeklyCount> createdByWeek, List<RecentStory> recent) {

    public BacklogInsights {
        byStatus = Collections.unmodifiableMap(new LinkedHashMap<>(byStatus));
        byPriority = Collections.unmodifiableMap(new LinkedHashMap<>(byPriority));
        createdByWeek = List.copyOf(createdByWeek);
        recent = List.copyOf(recent);
    }
}
