package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDocumentRepository {
    ProjectDocument save(ProjectDocument document);
    Optional<ProjectDocument> findByIdAndProjectIdAndStatus(UUID id, UUID projectId, DocumentStatus status);
    Optional<ProjectDocument> findByIdAndProjectIdAndStatusIn(UUID id, UUID projectId, Collection<DocumentStatus> statuses);
    List<ProjectDocument> findAllByProjectIdAndStatus(UUID projectId, DocumentStatus status);
    boolean existsByProjectIdAndNameAndStatus(UUID projectId, String name, DocumentStatus status);
    boolean existsByProjectIdAndNameAndIdNotAndStatus(UUID projectId, String name, UUID id, DocumentStatus status);
    int countByProjectIdAndStatus(UUID projectId, DocumentStatus status);
    void delete(ProjectDocument document);

    /** Deletes the documents (and their extracted text) and flushes, so their unique names are free at once. */
    void deleteAllAndFlush(Collection<ProjectDocument> documents);

    /**
     * The newest {@code limit} active documents of the project that carry a context summary, as
     * lightweight projections (the extracted text is not loaded). Feeds the AI project context.
     */
    List<DocumentContextSummary> findContextSummaries(UUID projectId, int limit);

    /** A document's name, type and context summary, without its extracted text. */
    record DocumentContextSummary(String name, String documentType, String summary) {}
}
