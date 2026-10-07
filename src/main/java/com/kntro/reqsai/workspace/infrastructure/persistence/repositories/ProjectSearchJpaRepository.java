package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Project;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lexical search over the tenant {@code projects} table for the global palette. Native because it relies
 * on the SQL search functions ({@code public.search_normalize}, {@code public.search_like_pattern},
 * {@code public.search_score}) and the pg_trgm {@code <%} / {@code %} operators: a name matches when it
 * contains the term (substring), has a word close to it, or is close as a whole, ignoring case and
 * accents. Exact, prefix and word-start matches rank above substring and fuzzy ones. Runs on the tenant
 * connection so {@code search_path} already targets the right schema. Returns {@code (id, name)} rows.
 */
public interface ProjectSearchJpaRepository extends JpaRepository<Project, UUID> {

    /** Owner/admin scope: every active project in the organization. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name
            from projects
            where organization_id = :organizationId
              and status = 'ACTIVE'
              and (public.search_normalize(name) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(name)
                   or public.search_normalize(name) % public.search_normalize(:term))
            order by public.search_score(name, :term) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchByOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("term") String term,
            Pageable pageable);

    /** Regular-member scope: only the caller's explicitly accessible projects. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name
            from projects
            where organization_id = :organizationId
              and status = 'ACTIVE'
              and id in (:projectIds)
              and (public.search_normalize(name) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(name)
                   or public.search_normalize(name) % public.search_normalize(:term))
            order by public.search_score(name, :term) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchByOrganizationAndIdIn(
            @Param("organizationId") UUID organizationId,
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("term") String term,
            Pageable pageable);
}
