package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectDocumentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectDocumentRepositoryAdapter implements ProjectDocumentRepository {

    private final ProjectDocumentJpaRepository jpa;

    @Override
    public ProjectDocument save(ProjectDocument document) {
        return jpa.save(document);
    }

    @Override
    public Optional<ProjectDocument> findByIdAndProjectIdAndStatus(UUID id, UUID projectId, DocumentStatus status) {
        return jpa.findByIdAndProjectIdAndStatus(id, projectId, status);
    }

    @Override
    public List<ProjectDocument> findAllByProjectIdAndStatus(UUID projectId, DocumentStatus status) {
        return jpa.findAllByProjectIdAndStatus(projectId, status);
    }

    @Override
    public boolean existsByProjectIdAndNameAndStatus(UUID projectId, String name, DocumentStatus status) {
        return jpa.existsByProjectIdAndNameAndStatus(projectId, name, status);
    }

    @Override
    public boolean existsByProjectIdAndNameAndIdNotAndStatus(UUID projectId, String name, UUID id, DocumentStatus status) {
        return jpa.existsByProjectIdAndNameAndIdNotAndStatus(projectId, name, id, status);
    }

    @Override
    public int countByProjectIdAndStatus(UUID projectId, DocumentStatus status) {
        return jpa.countByProjectIdAndStatus(projectId, status);
    }

    @Override
    public void delete(ProjectDocument document) {
        jpa.delete(document);
    }
}
