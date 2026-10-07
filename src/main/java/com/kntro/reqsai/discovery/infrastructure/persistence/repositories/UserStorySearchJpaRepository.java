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
 * Lexical search over the tenant {@code user_stories} table for the global palette. Native because it
 * relies on the SQL search functions ({@code public.search_normalize}, {@code public.search_like_pattern},
 * {@code public.search_score}) and the pg_trgm {@code <%} / {@code %} operators: a title matches when it
 * contains the term (substring), has a word close to it, or is close as a whole, ignoring case and
 * accents. Exact, prefix and word-start matches rank above substring and fuzzy ones. Runs on the tenant
 * connection so {@code search_path} already targets the right schema. Returns
 * {@code (id, title, project_id)} rows.
 */
public interface UserStorySearchJpaRepository extends JpaRepository<UserStory, UUID> {

    /** Owner/admin scope: every story in the tenant whose title matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, title, project_id
            from user_stories
            where (public.search_normalize(title) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(title)
                   or public.search_normalize(title) % public.search_normalize(:term))
            order by public.search_score(title, :term) desc, title asc
            """, nativeQuery = true)
    List<Object[]> searchAll(@Param("term") String term, Pageable pageable);

    /** Regular-member scope: stories in the caller's accessible projects whose title matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, title, project_id
            from user_stories
            where project_id in (:projectIds)
              and (public.search_normalize(title) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(title)
                   or public.search_normalize(title) % public.search_normalize(:term))
            order by public.search_score(title, :term) desc, title asc
            """, nativeQuery = true)
    List<Object[]> searchInProjects(
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("term") String term,
            Pageable pageable);
}
