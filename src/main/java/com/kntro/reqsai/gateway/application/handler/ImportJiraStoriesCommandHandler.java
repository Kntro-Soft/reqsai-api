package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.ImportJiraStoriesCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationJobLauncher;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.service.IntegrationSyncJobStarter;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Accepts a Jira import request as an <strong>asynchronous background job</strong>: validates the
 * target exists (409 {@code INTEGRATION_TARGET_NOT_CONFIGURED}), persists a RUNNING
 * {@code integration_sync_jobs} row (409 {@code INTEGRATION_JOB_ALREADY_RUNNING} when one is
 * already running), hands execution to the {@link IntegrationJobLauncher} and returns the job
 * snapshot for the 202 response. Progress streams on
 * {@code /topic/projects/{projectId}/integration-jobs} and is queryable via the job endpoints.
 * Deliberately not {@code @Transactional}: the job row must be committed (and visible to the async
 * worker and to reload queries) before the launch.
 */
@Component
@RequiredArgsConstructor
public class ImportJiraStoriesCommandHandler {

    private final ProjectIntegrationTargetRepository targets;
    private final IntegrationSyncJobStarter starter;
    private final IntegrationJobLauncher launcher;

    public IntegrationSyncJob handle(ImportJiraStoriesCommand command) {
        targets.findByProjectId(command.projectId())
                .orElseThrow(() -> IntegrationsExceptions.targetNotConfigured(command.projectId()));

        int knownTotal = command.issueKeys() == null ? 0 : command.issueKeys().size();
        IntegrationSyncJob job = starter.start(
                command.projectId(), IntegrationSyncJobType.IMPORT, knownTotal, command.requestedBy());
        launcher.launchImport(job.getId(), command.projectId(), command.issueKeys());
        return job;
    }
}
