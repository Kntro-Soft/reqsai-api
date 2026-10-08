package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the assistant knows about the project's backlog and sessions when it answers a chat question
 * ("¿cuántas historias hay?", "¿cuáles están aprobadas?", "¿cuándo fue la última sesión?").
 *
 * @param totalStories       number of stories in the backlog
 * @param storiesByStatus    how many stories are in each review status
 * @param stories            the newest stories, at most a few dozen
 * @param pendingSuggestions suggestions of the project still awaiting review
 * @param sessions           the newest discovery sessions
 */
public record BacklogOverview(
        long totalStories,
        Map<StoryStatus, Long> storiesByStatus,
        List<StoryLine> stories,
        long pendingSuggestions,
        List<SessionLine> sessions
) {

    public record StoryLine(String title, StoryStatus status, Priority priority, @Nullable Integer storyPoints,
                            int acceptanceCriteria) {}

    public record SessionLine(String title, Instant startedAt, SessionStatus status) {}
}
