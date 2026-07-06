package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Grouped aggregate queries for session history-table stats. Anchored on {@link UserStory} only to get a
 * JPA repository; the queries below group by {@code session_id} so each returns one row per session in
 * a single scan (no N+1).
 */
public interface SessionStatsJpaRepository extends JpaRepository<UserStory, UUID> {

    /**
     * Per-session story counts: total generated and how many are {@code APPROVED}. Only sessions with at
     * least one story appear; callers default the rest to zero.
     * <p>Row shape: {@code [sessionId: UUID, generated: long, accepted: long]}.
     */
    @Query("""
            select s.sessionId,
                   count(s),
                   sum(case when s.status = com.kntro.reqsai.discovery.domain.model.StoryStatus.APPROVED then 1L else 0L end)
            from UserStory s
            where s.sessionId in :sessionIds
            group by s.sessionId
            """)
    List<Object[]> storyCounts(@Param("sessionIds") Collection<UUID> sessionIds);

    /**
     * Per-session suggestion counts: pending suggestions and clarifying questions asked. Only sessions
     * with at least one suggestion appear; callers default the rest to zero.
     * <p>Row shape: {@code [sessionId: UUID, pending: long, questions: long]}.
     */
    @Query("""
            select g.sessionId,
                   sum(case when g.status = com.kntro.reqsai.discovery.domain.model.SuggestionStatus.PENDING then 1L else 0L end),
                   sum(case when g.type = com.kntro.reqsai.discovery.domain.model.SuggestionType.CLARIFYING_QUESTION then 1L else 0L end)
            from com.kntro.reqsai.discovery.domain.model.Suggestion g
            where g.sessionId in :sessionIds
            group by g.sessionId
            """)
    List<Object[]> suggestionCounts(@Param("sessionIds") Collection<UUID> sessionIds);
}
