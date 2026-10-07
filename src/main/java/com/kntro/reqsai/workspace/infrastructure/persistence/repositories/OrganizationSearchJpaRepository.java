package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Organization;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lexical search over the global {@code public.organizations} registry, scoped to the organizations the
 * caller belongs to. Native because it relies on the SQL search functions ({@code public.search_normalize},
 * {@code public.search_like_pattern}, {@code public.search_score}) and the pg_trgm {@code <%} / {@code %}
 * operators: a name or slug matches when it contains the term or is a fuzzy match, ignoring case and
 * accents. Returns {@code (id, name, slug)} rows, best match first.
 */
public interface OrganizationSearchJpaRepository extends JpaRepository<Organization, UUID> {

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name, slug
            from public.organizations
            where id in (:organizationIds)
              and (public.search_normalize(name) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(name)
                   or public.search_normalize(name) % public.search_normalize(:term)
                   or public.search_normalize(slug) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(slug)
                   or public.search_normalize(slug) % public.search_normalize(:term))
            order by greatest(public.search_score(name, :term), public.search_score(slug, :term)) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchWithinIds(
            @Param("organizationIds") Collection<UUID> organizationIds,
            @Param("term") String term,
            Pageable pageable);
}
