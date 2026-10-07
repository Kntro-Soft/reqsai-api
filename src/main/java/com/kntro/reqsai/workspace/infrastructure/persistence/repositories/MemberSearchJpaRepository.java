package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Lexical search over the global {@code public.members} registry, scoped to a single organization.
 * Native because it relies on the SQL search functions ({@code public.search_normalize},
 * {@code public.search_like_pattern}, {@code public.search_score}) and the pg_trgm {@code <%} / {@code %}
 * operators: a display name or email matches when it contains the term or is a fuzzy match, ignoring
 * case and accents. Returns {@code (id, display_name, email)} rows, best match first.
 */
public interface MemberSearchJpaRepository extends JpaRepository<Member, UUID> {

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, display_name, email
            from public.members
            where organization_id = :organizationId
              and (public.search_normalize(display_name) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(display_name)
                   or public.search_normalize(display_name) % public.search_normalize(:term)
                   or public.search_normalize(email) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(email)
                   or public.search_normalize(email) % public.search_normalize(:term))
            order by greatest(public.search_score(display_name, :term), public.search_score(email, :term)) desc,
              display_name asc
            """, nativeQuery = true)
    List<Object[]> searchByOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("term") String term,
            Pageable pageable);
}
