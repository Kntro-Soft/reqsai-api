package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.application.query.ListIntegrationJobsQuery;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lists a project's integration sync jobs: RUNNING only when {@code activeOnly} (what a reloaded
 * page asks first to re-attach its progress banner), else the most recent ~10 of any status.
 */
@Component
@RequiredArgsConstructor
public class ListIntegrationJobsQueryHandler {

    private final IntegrationSyncJobRepository jobs;

    @Transactional(readOnly = true)
    public List<IntegrationSyncJob> handle(ListIntegrationJobsQuery query) {
        return query.activeOnly() ? jobs.findRunning(query.projectId()) : jobs.findRecent(query.projectId());
    }
}
