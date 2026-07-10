package com.kntro.reqsai.gateway.infrastructure.persistence.adapters;

import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import com.kntro.reqsai.gateway.infrastructure.persistence.repositories.IntegrationSyncJobJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link IntegrationSyncJobRepository} port to Spring Data JPA. */
@Component
@RequiredArgsConstructor
public class IntegrationSyncJobRepositoryAdapter implements IntegrationSyncJobRepository {

    private final IntegrationSyncJobJpaRepository jpa;

    @Override
    public IntegrationSyncJob save(IntegrationSyncJob job) {
        return jpa.save(job);
    }

    @Override
    public Optional<IntegrationSyncJob> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public boolean existsRunning(UUID projectId, IntegrationSyncJobType type) {
        return jpa.existsByProjectIdAndJobTypeAndStatus(projectId, type, IntegrationSyncJobStatus.RUNNING);
    }

    @Override
    public List<IntegrationSyncJob> findRunning(UUID projectId) {
        return jpa.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, IntegrationSyncJobStatus.RUNNING);
    }

    @Override
    public List<IntegrationSyncJob> findRecent(UUID projectId) {
        return jpa.findTop10ByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
