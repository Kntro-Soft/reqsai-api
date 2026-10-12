package com.kntro.reqsai.codebase.application.port;

import com.kntro.reqsai.codebase.domain.model.CodeModule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeModuleRepository {

    CodeModule save(CodeModule module);

    /** The repository's modules ordered by path. */
    List<CodeModule> findAllByRepositoryId(UUID repositoryId);

    Optional<CodeModule> findByRepositoryIdAndPath(UUID repositoryId, String path);

    /** Every indexed module of the project's repositories. */
    List<CodeModule> findAllByProjectId(UUID projectId);

    /** The {@code limit} modules of the project nearest to {@code embedding} (cosine), nearest first. */
    List<CodeModule> findNearest(UUID projectId, float[] embedding, int limit);

    void deleteByRepositoryIdAndPathNotIn(UUID repositoryId, Collection<String> keptPaths);

    void deleteByRepositoryId(UUID repositoryId);
}
