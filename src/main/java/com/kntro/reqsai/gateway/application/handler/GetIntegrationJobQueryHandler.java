package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.application.query.GetIntegrationJobQuery;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches one integration sync job, scoped to the project in the path: a job id belonging to a
 * different project 404s ({@code INTEGRATION_JOB_NOT_FOUND}) just like a nonexistent one.
 */
@Component
@RequiredArgsConstructor
public class GetIntegrationJobQueryHandler {

    private final IntegrationSyncJobRepository jobs;

    @Transactional(readOnly = true)
    public IntegrationSyncJob handle(GetIntegrationJobQuery query) {
        return jobs.findById(query.jobId())
                .filter(job -> job.getProjectId().equals(query.projectId()))
                .orElseThrow(() -> IntegrationsExceptions.jobNotFound(query.jobId()));
    }
}
