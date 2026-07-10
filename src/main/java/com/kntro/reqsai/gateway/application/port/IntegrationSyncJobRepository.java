package com.kntro.reqsai.gateway.application.port;

import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link IntegrationSyncJob} rows — the durable state behind the async Jira
 * import / push-all endpoints. Implemented by {@code IntegrationSyncJobRepositoryAdapter}.
 */
public interface IntegrationSyncJobRepository {

    IntegrationSyncJob save(IntegrationSyncJob job);

    Optional<IntegrationSyncJob> findById(UUID id);

    /** Whether a {@code RUNNING} job of {@code type} already exists for the project (409 guard). */
    boolean existsRunning(UUID projectId, IntegrationSyncJobType type);

    /** The project's {@code RUNNING} jobs, newest first (reload recovery). */
    List<IntegrationSyncJob> findRunning(UUID projectId);

    /** The project's most recent jobs (any status, newest first, bounded to ~10). */
    List<IntegrationSyncJob> findRecent(UUID projectId);
}
