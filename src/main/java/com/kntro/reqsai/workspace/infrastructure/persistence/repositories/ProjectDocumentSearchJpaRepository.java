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
 * Trigram lexical search over the tenant {@code project_documents} table for the global palette. Native
 * for the pg_trgm {@code %} operator and {@code similarity()} ranking; runs on the tenant connection so
 * {@code search_path} already targets the right schema. Returns {@code (id, name, document_type, project_id)}
 * rows.
 */
public interface ProjectDocumentSearchJpaRepository extends JpaRepository<ProjectDocument, UUID> {

    /** Owner/admin scope: every document in the tenant whose name matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name, document_type, project_id
            from project_documents
            where name % :term
            order by similarity(name, :term) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchAll(@Param("term") String term, Pageable pageable);

    /** Regular-member scope: documents in the caller's accessible projects whose name matches. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name, document_type, project_id
            from project_documents
            where project_id in (:projectIds)
              and name % :term
            order by similarity(name, :term) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchInProjects(
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("term") String term,
            Pageable pageable);
}
