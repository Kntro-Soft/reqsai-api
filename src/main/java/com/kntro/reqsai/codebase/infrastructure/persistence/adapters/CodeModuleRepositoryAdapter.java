package com.kntro.reqsai.codebase.infrastructure.persistence.adapters;

import com.kntro.reqsai.codebase.application.port.CodeModuleRepository;
import com.kntro.reqsai.codebase.domain.model.CodeModule;
import com.kntro.reqsai.codebase.infrastructure.persistence.repositories.CodeModuleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

/** Adapts the {@link CodeModuleRepository} port to Spring Data JPA and pgvector. */
@Repository
@RequiredArgsConstructor
public class CodeModuleRepositoryAdapter implements CodeModuleRepository {

    private final CodeModuleJpaRepository jpa;

    @Override
    public CodeModule save(CodeModule module) {
        return jpa.save(module);
    }

    @Override
    public List<CodeModule> findAllByRepositoryId(UUID repositoryId) {
        return jpa.findAllByRepositoryIdOrderByPathAsc(repositoryId);
    }

    @Override
    public Optional<CodeModule> findByRepositoryIdAndPath(UUID repositoryId, String path) {
        return jpa.findByRepositoryIdAndPath(repositoryId, path);
    }

    @Override
    public List<CodeModule> findAllByProjectId(UUID projectId) {
        return jpa.findAllByProjectIdOrderByPathAsc(projectId);
    }

    @Override
    public List<CodeModule> findNearest(UUID projectId, float[] embedding, int limit) {
        return jpa.findNearest(projectId, toVectorLiteral(embedding), Math.max(1, limit));
    }

    @Override
    public void deleteByRepositoryIdAndPathNotIn(UUID repositoryId, Collection<String> keptPaths) {
        if (keptPaths.isEmpty()) {
            jpa.deleteByRepositoryId(repositoryId);
        } else {
            jpa.deleteByRepositoryIdAndPathNotIn(repositoryId, keptPaths);
        }
    }

    @Override
    public void deleteByRepositoryId(UUID repositoryId) {
        jpa.deleteByRepositoryId(repositoryId);
    }

    private static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
