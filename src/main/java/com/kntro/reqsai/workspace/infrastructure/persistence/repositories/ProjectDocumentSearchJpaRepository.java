package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lexical search over the tenant {@code project_documents} table for the global palette, with the same
 * case- and accent-insensitive substring/word/fuzzy matching and ranking as {@link ProjectSearchJpaRepository}.
 * Runs on the tenant connection so {@code search_path} already targets the right schema. Uploads still
 * awaiting the analyst's review ({@code PENDING}) are left out. Returns {@code (id, name, document_type,
 * project_id)} rows.
 */
public interface ProjectDocumentSearchJpaRepository extends JpaRepository<ProjectDocument, UUID> {

    /** Owner/admin scope: every document in the tenant whose name matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name, document_type, project_id
            from project_documents
            where status <> 'PENDING'
              and (public.search_normalize(name) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(name)
                   or public.search_normalize(name) % public.search_normalize(:term))
            order by public.search_score(name, :term) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchAll(@Param("term") String term, Pageable pageable);

    /** Regular-member scope: documents in the caller's accessible projects whose name matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name, document_type, project_id
            from project_documents
            where project_id in (:projectIds)
              and status <> 'PENDING'
              and (public.search_normalize(name) like public.search_like_pattern(:term)
                   or public.search_normalize(:term) <% public.search_normalize(name)
                   or public.search_normalize(name) % public.search_normalize(:term))
            order by public.search_score(name, :term) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchInProjects(
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("term") String term,
            Pageable pageable);
}
