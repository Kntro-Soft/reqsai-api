package com.kntro.reqsai.discovery.insights;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The discovery activity of a set of projects.
 * <p>
 * Suggestion figures cover story proposals (new story, update, edge case);
 * clarifying questions are counted apart because they are answered, not
 * accepted.
 *
 * @param total
 *            sessions of any status
 * @param byStatus
 *            session count per status, every status present, in lifecycle order
 * @param createdByWeek
 *            sessions created per week since the requested date (weeks without
 *            any are absent)
 * @param suggestionsAccepted
 *            story proposals the analyst accepted
 * @param suggestionsDismissed
 *            story proposals the analyst dismissed
 * @param suggestionsPending
 *            story proposals still awaiting a decision
 * @param questionsAsked
 *            clarifying questions the copilot raised
 * @param recent
 *            the newest sessions
 */
public record SessionInsights(long total, Map<String, Long> byStatus, List<WeeklyCount> createdByWeek,
        long suggestionsAccepted, long suggestionsDismissed, long suggestionsPending, long questionsAsked,
        List<RecentSession> recent) {

    public SessionInsights {
        byStatus = Collections.unmodifiableMap(new LinkedHashMap<>(byStatus));
        createdByWeek = List.copyOf(createdByWeek);
        recent = List.copyOf(recent);
    }
}
