package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Native grouped aggregates behind the dashboards. Anchored on
 * {@link UserStory} only to get a JPA repository; every query counts in SQL
 * over the given project ids and returns plain rows, so no entity is hydrated.
 * They run on the tenant connection, so {@code search_path} already targets the
 * right schema.
 * <p>
 * A story is <em>live</em> while it is {@code DRAFT}, {@code APPROVED} or
 * {@code EXPORTED}. Weeks start on Monday in UTC and come back as
 * {@code YYYY-MM-DD} text.
 */
public interface DiscoveryInsightsJpaRepository extends JpaRepository<UserStory, UUID> {

    /** Row shape: {@code [status: text, count: long]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select status, count(*)
            from user_stories
            where project_id in (:projectIds)
            group by status
            """, nativeQuery = true)
    List<Object[]> storiesByStatus(@Param("projectIds") Collection<UUID> projectIds);

    /** Row shape: {@code [priority: text, count: long]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select priority, count(*)
            from user_stories
            where project_id in (:projectIds)
            group by priority
            """, nativeQuery = true)
    List<Object[]> storiesByPriority(@Param("projectIds") Collection<UUID> projectIds);

    /**
     * One row:
     * {@code [live, points, approvedPoints, unestimated, withCriteria, withOrigin, linkedToCode]}.
     */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select count(*) filter (where s.status in ('DRAFT', 'APPROVED', 'EXPORTED')),
                   coalesce(sum(s.story_points) filter (where s.status in ('DRAFT', 'APPROVED', 'EXPORTED')), 0),
                   coalesce(sum(s.story_points) filter (where s.status in ('APPROVED', 'EXPORTED')), 0),
                   count(*) filter (where s.status in ('DRAFT', 'APPROVED', 'EXPORTED') and s.story_points is null),
                   count(*) filter (where s.status in ('DRAFT', 'APPROVED', 'EXPORTED')
                                      and exists (select 1 from acceptance_criteria ac where ac.story_id = s.id)),
                   count(*) filter (where s.status in ('DRAFT', 'APPROVED', 'EXPORTED')
                                      and s.origin_quote is not null and s.origin_quote <> ''),
                   count(*) filter (where s.status in ('DRAFT', 'APPROVED', 'EXPORTED')
                                      and jsonb_array_length(s.code_refs) > 0)
            from user_stories s
            where s.project_id in (:projectIds)
            """, nativeQuery = true)
    List<Object[]> storyTotals(@Param("projectIds") Collection<UUID> projectIds);

    /** Row shape: {@code [week: text, count: long]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select to_char(date_trunc('week', created_at at time zone 'UTC'), 'YYYY-MM-DD') as week, count(*)
            from user_stories
            where project_id in (:projectIds) and created_at >= :since
            group by week
            order by week
            """, nativeQuery = true)
    List<Object[]> storiesByWeek(@Param("projectIds") Collection<UUID> projectIds, @Param("since") Instant since);

    /** Row shape: {@code [id, projectId, title, status, priority, createdAt]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, project_id, title, status, priority, created_at
            from user_stories
            where project_id in (:projectIds)
            order by created_at desc, id desc
            """, nativeQuery = true)
    List<Object[]> recentStories(@Param("projectIds") Collection<UUID> projectIds, Pageable pageable);

    /** Row shape: {@code [status: text, count: long]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select status, count(*)
            from discovery_sessions
            where project_id in (:projectIds)
            group by status
            """, nativeQuery = true)
    List<Object[]> sessionsByStatus(@Param("projectIds") Collection<UUID> projectIds);

    /** Row shape: {@code [week: text, count: long]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select to_char(date_trunc('week', created_at at time zone 'UTC'), 'YYYY-MM-DD') as week, count(*)
            from discovery_sessions
            where project_id in (:projectIds) and created_at >= :since
            group by week
            order by week
            """, nativeQuery = true)
    List<Object[]> sessionsByWeek(@Param("projectIds") Collection<UUID> projectIds, @Param("since") Instant since);

    /** Row shape: {@code [id, projectId, title, status, createdAt]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, project_id, title, status, created_at
            from discovery_sessions
            where project_id in (:projectIds)
            order by created_at desc, id desc
            """, nativeQuery = true)
    List<Object[]> recentSessions(@Param("projectIds") Collection<UUID> projectIds, Pageable pageable);

    /**
     * One row: {@code [accepted, dismissed, pending, questions]}; story proposals
     * exclude the questions.
     */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select count(*) filter (where type <> 'CLARIFYING_QUESTION' and status = 'ACCEPTED'),
                   count(*) filter (where type <> 'CLARIFYING_QUESTION' and status = 'DISMISSED'),
                   count(*) filter (where type <> 'CLARIFYING_QUESTION' and status = 'PENDING'),
                   count(*) filter (where type = 'CLARIFYING_QUESTION')
            from suggestions
            where project_id in (:projectIds)
            """, nativeQuery = true)
    List<Object[]> suggestionTotals(@Param("projectIds") Collection<UUID> projectIds);

    /** Row shape: {@code [projectId, stories: long, lastStoryAt]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select project_id, count(*), max(created_at)
            from user_stories
            where project_id in (:projectIds)
            group by project_id
            """, nativeQuery = true)
    List<Object[]> storyActivity(@Param("projectIds") Collection<UUID> projectIds);

    /** Row shape: {@code [projectId, sessions: long, lastSessionAt]}. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select project_id, count(*), max(created_at)
            from discovery_sessions
            where project_id in (:projectIds)
            group by project_id
            """, nativeQuery = true)
    List<Object[]> sessionActivity(@Param("projectIds") Collection<UUID> projectIds);
}
