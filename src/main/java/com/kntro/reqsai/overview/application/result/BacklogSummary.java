package com.kntro.reqsai.overview.application.result;

import java.util.List;
import java.util.Map;

/**
 * The backlog of a project or of every project a caller may see.
 *
 * @param total
 *            stories of any status
 * @param byStatus
 *            stories per status, every status present
 * @param byPriority
 *            stories per priority, every priority present
 * @param storyPoints
 *            estimation of the live backlog
 * @param coverage
 *            how well the live stories are backed up
 * @param createdByWeek
 *            stories created per week over the window, every week present
 * @param recent
 *            the newest stories (empty on the organization page)
 */
public record BacklogSummary(long total, Map<String, Long> byStatus, Map<String, Long> byPriority,
        StoryPoints storyPoints, StoryCoverage coverage, List<WeekCount> createdByWeek, List<StorySummary> recent) {
}
