package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.gateway.application.command.PushAllStoriesCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationJobLauncher;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.service.IntegrationSyncJobStarter;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Accepts a push-all request as an <strong>asynchronous background job</strong>: validates the
 * target exists (409 {@code INTEGRATION_TARGET_NOT_CONFIGURED}), persists a RUNNING
 * {@code integration_sync_jobs} row (409 {@code INTEGRATION_JOB_ALREADY_RUNNING} when one is
 * already running), hands execution to the {@link IntegrationJobLauncher} and returns the job
 * snapshot for the 202 response. The known story count seeds {@code total} immediately so the
 * progress banner can render a meaningful bar from the first frame. Deliberately not
 * {@code @Transactional}: the job row must be committed before the launch.
 */
@Component
@RequiredArgsConstructor
public class PushAllStoriesCommandHandler {

    private final ProjectIntegrationTargetRepository targets;
    private final DiscoveryStoryReadPort stories;
    private final IntegrationSyncJobStarter starter;
    private final IntegrationJobLauncher launcher;

    public IntegrationSyncJob handle(PushAllStoriesCommand command) {
        targets.findByProjectId(command.projectId())
                .orElseThrow(() -> IntegrationsExceptions.targetNotConfigured(command.projectId()));

        int knownTotal = stories.listStories(command.projectId()).size();
        IntegrationSyncJob job = starter.start(
                command.projectId(), IntegrationSyncJobType.PUSH_ALL, knownTotal, command.requestedBy());
        launcher.launchPushAll(job.getId(), command.projectId());
        return job;
    }
}
