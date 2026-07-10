package com.kntro.reqsai.gateway.infrastructure.persistence.repositories;

import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link IntegrationSyncJob}; internal to the persistence adapter. */
public interface IntegrationSyncJobJpaRepository extends JpaRepository<IntegrationSyncJob, UUID> {

    boolean existsByProjectIdAndJobTypeAndStatus(
            UUID projectId, IntegrationSyncJobType jobType, IntegrationSyncJobStatus status);

    List<IntegrationSyncJob> findByProjectIdAndStatusOrderByCreatedAtDesc(
            UUID projectId, IntegrationSyncJobStatus status);

    List<IntegrationSyncJob> findTop10ByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
