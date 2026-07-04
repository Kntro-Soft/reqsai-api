package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.SessionStatsRepository;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.SessionStatsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapts {@link SessionStatsRepository} to two grouped JPA queries (stories, suggestions) merged in
 * memory. Two scans total regardless of page size — no per-session round trip.
 */
@Repository
@RequiredArgsConstructor
public class SessionStatsRepositoryAdapter implements SessionStatsRepository {

    private final SessionStatsJpaRepository jpa;

    @Override
    public Map<UUID, SessionStats> statsForSessions(Collection<UUID> sessionIds) {
        Map<UUID, SessionStats> result = new HashMap<>();
        if (sessionIds.isEmpty()) {
            return result;
        }

        // Seed every requested session with zeros so callers never null-check.
        for (UUID id : sessionIds) {
            result.put(id, SessionStats.zero());
        }

        List<Object[]> storyRows = jpa.storyCounts(sessionIds);
        for (Object[] row : storyRows) {
            UUID sessionId = (UUID) row[0];
            long generated = toLong(row[1]);
            long accepted = toLong(row[2]);
            SessionStats current = result.get(sessionId);
            result.put(sessionId, new SessionStats(
                    generated, accepted, current.suggestionsPending(), current.questionsAsked()));
        }

        List<Object[]> suggestionRows = jpa.suggestionCounts(sessionIds);
        for (Object[] row : suggestionRows) {
            UUID sessionId = (UUID) row[0];
            long pending = toLong(row[1]);
            long questions = toLong(row[2]);
            SessionStats current = result.get(sessionId);
            result.put(sessionId, new SessionStats(
                    current.storiesGenerated(), current.storiesAccepted(), pending, questions));
        }

        return result;
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
