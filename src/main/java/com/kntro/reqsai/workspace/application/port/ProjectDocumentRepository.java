package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDocumentRepository {
    ProjectDocument save(ProjectDocument document);
    Optional<ProjectDocument> findByIdAndProjectIdAndStatus(UUID id, UUID projectId, DocumentStatus status);
    List<ProjectDocument> findAllByProjectIdAndStatus(UUID projectId, DocumentStatus status);
    boolean existsByProjectIdAndNameAndStatus(UUID projectId, String name, DocumentStatus status);
    boolean existsByProjectIdAndNameAndIdNotAndStatus(UUID projectId, String name, UUID id, DocumentStatus status);
    int countByProjectIdAndStatus(UUID projectId, DocumentStatus status);
    void delete(ProjectDocument document);
}
