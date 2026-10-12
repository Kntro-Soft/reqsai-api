package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.api.ProjectSetupParticipant;
import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.api.SetupStepView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gateway's part in a project's setup: it refuses a Jira link whose connection does not exist, and reads the
 * Jira steps from what already persists (the project's target and its latest import job).
 */
@Component
@RequiredArgsConstructor
class JiraSetupParticipant implements ProjectSetupParticipant {

    private final ProjectJiraSetup jira;
    private final ProjectIntegrationTargetRepository targets;
    private final IntegrationSyncJobRepository jobs;

    @Override
    public void validate(ProjectSources sources, UUID requestedBy) {
        if (sources.jira() == null) return;
        jira.connectionFor(UUID.fromString(TenantContext.getCurrentTenant()), sources.jira().connectionId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SetupStepView> steps(UUID projectId) {
        List<SetupStepView> steps = new ArrayList<>();
        if (targets.findByProjectId(projectId).isPresent()) {
            steps.add(new SetupStepView(SetupStepKeys.JIRA_LINK, SetupStepStatus.DONE, 0, 0, null));
        }
        jobs.findRecent(projectId).stream()
                .filter(job -> job.getJobType() == IntegrationSyncJobType.IMPORT)
                .findFirst()
                .map(JiraSetupParticipant::importStep)
                .ifPresent(steps::add);
        return steps;
    }

    private static SetupStepView importStep(IntegrationSyncJob job) {
        return switch (job.getStatus()) {
            case RUNNING -> new SetupStepView(SetupStepKeys.JIRA_IMPORT, SetupStepStatus.RUNNING,
                    job.getProcessed(), job.getTotal(), null);
            case COMPLETED -> new SetupStepView(SetupStepKeys.JIRA_IMPORT, SetupStepStatus.DONE,
                    job.getSucceeded(), job.getTotal(), job.getFailed() > 0 ? "PARTIAL:" + job.getFailed() : null);
            case FAILED -> new SetupStepView(SetupStepKeys.JIRA_IMPORT, SetupStepStatus.FAILED,
                    job.getProcessed(), job.getTotal(), job.getMessage());
        };
    }
}
