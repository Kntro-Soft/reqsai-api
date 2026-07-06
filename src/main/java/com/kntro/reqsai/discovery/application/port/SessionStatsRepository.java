package com.kntro.reqsai.discovery.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only port for discovery-session aggregate counts (history-table stats). Tenant-scoped.
 * Every count is computed with a single grouped query over the given session ids — there is no
 * per-session round trip, so the list endpoint stays free of N+1.
 */
public interface SessionStatsRepository {

    /**
     * Aggregate counts for each of {@code sessionIds}, keyed by session id. Sessions with no stories
     * or suggestions are still present in the map with {@link SessionStats#zero()} values, so the
     * caller never has to null-check.
     */
    Map<UUID, SessionStats> statsForSessions(Collection<UUID> sessionIds);

    /**
     * Aggregate counts derivable from the stories/suggestions tables.
     *
     * @param storiesGenerated   user stories whose {@code session_id} is this session
     * @param storiesAccepted    of those, how many reached {@code APPROVED}
     * @param suggestionsPending suggestions of this session still {@code PENDING}
     * @param questionsAsked     suggestions of this session of type {@code CLARIFYING_QUESTION}
     */
    record SessionStats(
            long storiesGenerated,
            long storiesAccepted,
            long suggestionsPending,
            long questionsAsked) {

        public static SessionStats zero() {
            return new SessionStats(0, 0, 0, 0);
        }
    }
}
