package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Creates the durable {@code RUNNING} job row shared by both async endpoints, enforcing the
 * single-running-job rule twice: a cheap pre-check (the common 409 path) and the partial unique
 * index {@code uq_integration_sync_jobs_running} as the race-proof backstop (a concurrent insert
 * surfaces as the same 409). Deliberately <strong>not</strong> wrapped in a caller transaction:
 * the save commits on its own, so the row is visible to the async worker (and to job queries)
 * before the worker is dispatched.
 */
@Component
@RequiredArgsConstructor
public class IntegrationSyncJobStarter {

    private final IntegrationSyncJobRepository jobs;

    /**
     * Persists a new {@code RUNNING} job of {@code type} for the project.
     *
     * @param total the item count if already known (selected issue keys / story count), else 0
     * @throws com.kntro.reqsai.shared.domain.exception.DomainException 409
     *         {@code INTEGRATION_JOB_ALREADY_RUNNING} when a job of the same type is running
     */
    public IntegrationSyncJob start(UUID projectId, IntegrationSyncJobType type, int total, @Nullable UUID requestedBy) {
        if (jobs.existsRunning(projectId, type)) {
            throw IntegrationsExceptions.jobAlreadyRunning(projectId, type.name());
        }
        try {
            return jobs.save(new IntegrationSyncJob(projectId, type, total, requestedBy));
        } catch (DataIntegrityViolationException e) {
            // Two requests raced past the pre-check; the partial unique index kept exactly one.
            throw IntegrationsExceptions.jobAlreadyRunning(projectId, type.name());
        }
    }
}
