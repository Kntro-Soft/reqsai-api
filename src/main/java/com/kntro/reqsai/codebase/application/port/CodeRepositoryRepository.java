package com.kntro.reqsai.codebase.application.port;

import com.kntro.reqsai.codebase.domain.model.CodeRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeRepositoryRepository {

    CodeRepository save(CodeRepository repository);

    Optional<CodeRepository> findById(UUID id);

    Optional<CodeRepository> findByIdAndProjectId(UUID id, UUID projectId);

    /** The project's repositories, oldest first. */
    List<CodeRepository> findAllByProjectId(UUID projectId);

    boolean existsByProjectIdAndFullName(UUID projectId, String owner, String name);

    void delete(CodeRepository repository);
}
