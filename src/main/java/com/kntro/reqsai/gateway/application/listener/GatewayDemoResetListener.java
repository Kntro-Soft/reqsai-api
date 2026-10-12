package com.kntro.reqsai.gateway.application.listener;

import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.workspace.api.DemoProjectSeededIntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Resets the demo project's integrations whenever Workspace (re)seeds it (US28): the Jira target (project key
 * and issue type) and the push history are forgotten. The organization's connections (the Jira site) are not
 * the project's and stay.
 * <p>
 * A plain synchronous {@link EventListener}, like Discovery's: it runs inside the seeding transaction with the
 * tenant bound, so the restore commits or rolls back as one unit.
 */
@Component
@RequiredArgsConstructor
class GatewayDemoResetListener {

    private final ProjectIntegrationTargetRepository targets;
    private final IntegrationSyncJobRepository jobs;

    @EventListener
    void onDemoProjectSeeded(DemoProjectSeededIntegrationEvent event) {
        targets.findByProjectId(event.projectId()).ifPresent(targets::delete);
        jobs.deleteByProjectId(event.projectId());
    }
}
