package com.kntro.reqsai.codebase.infrastructure.persistence.adapters;

import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.codebase.infrastructure.persistence.repositories.CodeRepositoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link CodeRepositoryRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class CodeRepositoryRepositoryAdapter implements CodeRepositoryRepository {

    private final CodeRepositoryJpaRepository jpa;

    @Override
    public CodeRepository save(CodeRepository repository) {
        return jpa.save(repository);
    }

    @Override
    public Optional<CodeRepository> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<CodeRepository> findByIdAndProjectId(UUID id, UUID projectId) {
        return jpa.findByIdAndProjectId(id, projectId);
    }

    @Override
    public List<CodeRepository> findAllByProjectId(UUID projectId) {
        return jpa.findAllByProjectIdOrderByCreatedAtAsc(projectId);
    }

    @Override
    public boolean existsByProjectIdAndFullName(UUID projectId, String owner, String name) {
        return jpa.existsByProjectIdAndFullName(projectId, owner, name);
    }

    @Override
    public List<CodeRepository> findAllByInstallationId(long installationId) {
        return jpa.findAllByInstallationId(installationId);
    }

    @Override
    public void delete(CodeRepository repository) {
        jpa.delete(repository);
    }
}
