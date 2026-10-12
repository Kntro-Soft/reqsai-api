package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.api.ProjectSetupParticipant;
import com.kntro.reqsai.workspace.api.ProjectSetupRequestedIntegrationEvent;
import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.application.notification.ProjectSetupNotifier;
import com.kntro.reqsai.workspace.application.port.ProjectSetupStepRepository;
import com.kntro.reqsai.workspace.application.result.ProjectSetup;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.ProjectSetupStep;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The wiring of a new project to its external sources. It checks what can be checked at once (so a bad request is
 * answered before anything is created), records the steps the creator asked for and announces them for the source
 * modules to run in the background; it also serves the skip and retry the setup bar offers. It knows no source
 * module: those implement {@link ProjectSetupParticipant} and listen to the announcement.
 */
@Component
@RequiredArgsConstructor
public class ProjectSetupService {

    private final ProjectSetupStepRepository steps;
    private final ProjectSetupViews views;
    private final ProjectSetupNotifier notifier;
    private final ObjectProvider<ProjectSetupParticipant> participants;
    private final ApplicationEventPublisher events;

    /** Lets every source module refuse sources it cannot use. Throws a 4xx domain exception. */
    public void validate(ProjectSources sources, UUID requestedBy) {
        if (sources.isEmpty()) return;
        participants.orderedStream().forEach(participant -> participant.validate(sources, requestedBy));
    }

    /** Records the requested steps and announces them; call inside the transaction that creates the project. */
    @Transactional
    public void request(UUID organizationId, UUID projectId, UUID requestedBy, ProjectSources sources) {
        if (sources.isEmpty()) return;
        List<String> keys = new ArrayList<>();
        if (sources.jira() != null) {
            keys.add(SetupStepKeys.JIRA_LINK);
            if (sources.jira().importStories()) keys.add(SetupStepKeys.JIRA_IMPORT);
        }
        if (!sources.github().isEmpty()) keys.add(SetupStepKeys.GITHUB_REPOS);
        keys.forEach(key -> steps.save(new ProjectSetupStep(projectId, key, sources)));
        events.publishEvent(ProjectSetupRequestedIntegrationEvent.of(organizationId, projectId, requestedBy,
                sources, keys));
    }

    @Transactional(readOnly = true)
    public ProjectSetup get(UUID projectId) {
        return views.of(projectId);
    }

    /** The user stops waiting for a step. Work already started keeps running; it is just no longer followed. */
    @Transactional
    public ProjectSetup skip(UUID projectId, String stepKey) {
        ProjectSetupStep step = find(projectId, stepKey);
        step.skip();
        steps.save(step);
        notifier.changed(projectId);
        return views.of(projectId);
    }

    /**
     * Tries a failed step again. Retrying the Jira link also retries the import that waited on it. The work is
     * announced like on creation and done on behalf of the user who asks.
     */
    @Transactional
    public ProjectSetup retry(UUID projectId, String stepKey, UUID requestedBy) {
        ProjectSetupStep step = find(projectId, stepKey);
        ProjectSources sources = step.getRequest();
        ProjectSetup current = views.of(projectId);
        boolean failed = current.steps().stream()
                .anyMatch(view -> view.key().equals(stepKey) && view.status() == SetupStepStatus.FAILED);
        if (!failed || sources == null) {
            throw WorkspaceExceptions.setupStepNotRetriable(stepKey);
        }
        List<String> keys = new ArrayList<>(List.of(stepKey));
        if (SetupStepKeys.JIRA_LINK.equals(stepKey)) {
            steps.findByProjectIdAndStepKey(projectId, SetupStepKeys.JIRA_IMPORT)
                    .filter(importStep -> importStep.getStatus() != SetupStepStatus.SKIPPED)
                    .ifPresent(importStep -> keys.add(SetupStepKeys.JIRA_IMPORT));
        }
        for (String key : keys) {
            ProjectSetupStep toReset = find(projectId, key);
            toReset.retry();
            steps.save(toReset);
        }
        UUID organizationId = UUID.fromString(TenantContext.getCurrentTenant());
        events.publishEvent(ProjectSetupRequestedIntegrationEvent.of(organizationId, projectId, requestedBy,
                sources, keys));
        notifier.changed(projectId);
        return views.of(projectId);
    }

    private ProjectSetupStep find(UUID projectId, String stepKey) {
        return steps.findByProjectIdAndStepKey(projectId, stepKey)
                .orElseThrow(() -> WorkspaceExceptions.setupStepNotFound(projectId, stepKey));
    }
}
