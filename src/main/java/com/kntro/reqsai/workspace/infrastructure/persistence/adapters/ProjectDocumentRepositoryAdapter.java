package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectDocumentJpaRepository;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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
    public Optional<ProjectDocument> findByIdAndProjectIdAndStatusIn(
            UUID id, UUID projectId, Collection<DocumentStatus> statuses) {
        return jpa.findByIdAndProjectIdAndStatusIn(id, projectId, statuses);
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

    @Override
    @Transactional
    public void deleteAllAndFlush(Collection<ProjectDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        jpa.deleteAll(documents);
        jpa.flush();
    }

    @Override
    public List<DocumentContextSummary> findContextSummaries(UUID projectId, int limit) {
        return jpa.findContextSummaries(projectId, PageRequest.of(0, limit)).stream()
                .map(row -> new DocumentContextSummary(
                        (String) row[0], ((DocumentType) row[1]).name(), (String) row[2]))
                .toList();
    }
}
