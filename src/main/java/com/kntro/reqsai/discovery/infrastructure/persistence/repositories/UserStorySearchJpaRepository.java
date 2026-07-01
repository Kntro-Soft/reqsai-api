package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Trigram lexical search over the tenant {@code user_stories} table for the global palette. Native for
 * the pg_trgm {@code %} operator and {@code similarity()} ranking; runs on the tenant connection so
 * {@code search_path} already targets the right schema. Returns {@code (id, title, project_id)} rows.
 */
public interface UserStorySearchJpaRepository extends JpaRepository<UserStory, UUID> {

    /** Owner/admin scope: every story in the tenant whose title matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, title, project_id
            from user_stories
            where title % :term
            order by similarity(title, :term) desc, title asc
            """, nativeQuery = true)
    List<Object[]> searchAll(@Param("term") String term, Pageable pageable);

    /** Regular-member scope: stories in the caller's accessible projects whose title matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, title, project_id
            from user_stories
            where project_id in (:projectIds)
              and title % :term
            order by similarity(title, :term) desc, title asc
            """, nativeQuery = true)
    List<Object[]> searchInProjects(
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("term") String term,
            Pageable pageable);
}
