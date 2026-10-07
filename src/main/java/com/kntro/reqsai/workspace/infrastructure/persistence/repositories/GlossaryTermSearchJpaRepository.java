package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lexical search over the tenant {@code glossary_terms} table for the global palette, with the same
 * case- and accent-insensitive substring/word/fuzzy matching and ranking as
 * {@link ProjectSearchJpaRepository}. Runs on the tenant connection so {@code search_path} already targets
 * the right schema. The owning project id is resolved through the
 * {@code glossaries} table ({@code glossary_terms.glossary_id -> glossaries.id -> glossaries.project_id}),
 * so the row shape is {@code (id, term, definition, project_id)}.
 */
public interface GlossaryTermSearchJpaRepository extends JpaRepository<GlossaryTerm, UUID> {

    /** Owner/admin scope: every glossary term in the tenant whose term matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select t.id, t.term, t.definition, g.project_id
            from glossary_terms t
              join glossaries g on g.id = t.glossary_id
            where (public.search_normalize(t.term) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(t.term)
                   or public.search_normalize(t.term) % public.search_normalize(:term))
            order by public.search_score(t.term, :term) desc, t.term asc
            """, nativeQuery = true)
    List<Object[]> searchAll(@Param("term") String term, Pageable pageable);

    /** Regular-member scope: glossary terms in the caller's accessible projects whose term matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select t.id, t.term, t.definition, g.project_id
            from glossary_terms t
              join glossaries g on g.id = t.glossary_id
            where g.project_id in (:projectIds)
              and (public.search_normalize(t.term) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(t.term)
                   or public.search_normalize(t.term) % public.search_normalize(:term))
            order by public.search_score(t.term, :term) desc, t.term asc
            """, nativeQuery = true)
    List<Object[]> searchInProjects(
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("term") String term,
            Pageable pageable);

    /**
     * Paginated glossary terms of a single project (scoped through the {@code glossary} relation) with
     * an optional case-insensitive substring {@code search} over term + definition. The {@code :search}
     * is null-guarded so a {@code null}/blank argument disables the text filter and returns the whole
     * (paginated) glossary. Sorting/paging come from the {@link Pageable}. JPQL (not native) so it
     * returns a proper {@code Page<GlossaryTerm>} with an accurate total count.
     */
    @Query("""
            select t from GlossaryTerm t
            where t.glossary.projectId = :projectId
              and (cast(:search as string) is null
                   or lower(t.term) like lower(concat('%', cast(:search as string), '%'))
                   or lower(t.definition) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<GlossaryTerm> findPageByProjectId(
            @Param("projectId") UUID projectId,
            @Param("search") @Nullable String search,
            Pageable pageable);
}
