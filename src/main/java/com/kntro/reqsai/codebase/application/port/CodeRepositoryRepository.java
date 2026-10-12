package com.kntro.reqsai.codebase.application.port;

import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.codebase.domain.model.CodeRepositoryStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
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

    /** Every repository of the tenant read through a GitHub App installation. */
    List<CodeRepository> findAllByInstallationId(long installationId);

    void delete(CodeRepository repository);

    /** Repository counts per status for the given projects, one grouped statement. */
    List<StatusSummary> summarizeByStatus(Collection<UUID> projectIds);

    /**
     * @param status        the repositories' status
     * @param repositories  how many repositories have it
     * @param files         files read across them
     * @param lastIndexedAt newest indexing among them, null when none was indexed
     */
    record StatusSummary(CodeRepositoryStatus status, long repositories, long files,
                         @Nullable Instant lastIndexedAt) {
    }
}
