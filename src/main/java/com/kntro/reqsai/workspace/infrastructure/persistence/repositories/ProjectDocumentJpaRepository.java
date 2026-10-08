package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDocumentJpaRepository extends JpaRepository<ProjectDocument, UUID> {

    Optional<ProjectDocument> findByIdAndProjectIdAndStatus(UUID id, UUID projectId, DocumentStatus status);

    Optional<ProjectDocument> findByIdAndProjectIdAndStatusIn(UUID id, UUID projectId, Collection<DocumentStatus> statuses);

    List<ProjectDocument> findAllByProjectIdAndStatus(UUID projectId, DocumentStatus status);

    @Query("""
            SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END
            FROM ProjectDocument d
            WHERE d.projectId = :projectId
              AND lower(d.name) = lower(:name)
              AND d.status = :status
            """)
    boolean existsByProjectIdAndNameAndStatus(UUID projectId, String name, DocumentStatus status);

    @Query("""
            SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END
            FROM ProjectDocument d
            WHERE d.projectId = :projectId
              AND lower(d.name) = lower(:name)
              AND d.id <> :id
              AND d.status = :status
            """)
    boolean existsByProjectIdAndNameAndIdNotAndStatus(UUID projectId, String name, UUID id, DocumentStatus status);

    int countByProjectIdAndStatus(UUID projectId, DocumentStatus status);

    /** {@code (name, documentType, summary)} of the newest active documents that have a summary. */
    @Query("""
            SELECT d.name, d.documentType, d.summary
            FROM ProjectDocument d
            WHERE d.projectId = :projectId
              AND d.status = com.kntro.reqsai.workspace.domain.model.DocumentStatus.ACTIVE
              AND d.summary IS NOT NULL
            ORDER BY d.createdAt DESC
            """)
    List<Object[]> findContextSummaries(UUID projectId, Pageable pageable);
}
