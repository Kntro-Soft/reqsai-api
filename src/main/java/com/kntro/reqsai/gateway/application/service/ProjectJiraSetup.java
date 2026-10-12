package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.command.ImportJiraStoriesCommand;
import com.kntro.reqsai.gateway.application.command.SaveProjectTargetCommand;
import com.kntro.reqsai.gateway.application.handler.ImportJiraStoriesCommandHandler;
import com.kntro.reqsai.gateway.application.handler.SaveProjectTargetCommandHandler;
import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.api.ProjectSetupRequestedIntegrationEvent;
import com.kntro.reqsai.workspace.api.ProjectSetupReporter;
import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Wires a new project to Jira as its creator asked: saves the Jira project and issue type as the project's push
 * target and, when requested, starts the import of its issues, with the same handlers the REST endpoints use and on
 * behalf of the creator. Each step reports its outcome to Workspace; a failure is recorded and logged, never thrown,
 * so the project stays.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectJiraSetup {

    static final String UNEXPECTED = "SETUP_STEP_FAILED";

    private final IntegrationConnectionRepository connections;
    private final SaveProjectTargetCommandHandler saveTarget;
    private final ImportJiraStoriesCommandHandler importStories;
    private final WorkspaceModuleApi workspace;
    private final ProjectSetupReporter setup;

    /**
     * The organization's Jira connection: the one asked for, or its active one when none was.
     *
     * @throws DomainException 404 when the connection is unknown or the organization has none
     */
    public IntegrationConnection connectionFor(UUID organizationId, @Nullable UUID connectionId) {
        if (connectionId != null) {
            return connections.findByIdAndOrganizationId(connectionId, organizationId)
                    .filter(connection -> connection.getProvider() == IntegrationProviderType.JIRA)
                    .orElseThrow(() -> IntegrationsExceptions.connectionNotFound(connectionId));
        }
        return connections.findAllByOrganizationId(organizationId).stream()
                .filter(connection -> connection.getProvider() == IntegrationProviderType.JIRA)
                .filter(connection -> connection.getStatus() != ConnectionStatus.DISCONNECTED)
                .findFirst()
                .orElseThrow(() -> IntegrationsExceptions.jiraNotConnected(organizationId));
    }

    /**
     * Runs the Jira steps the event asks for, in the tenant of the event. The listener's own transaction was opened
     * before the tenant was restored, so this suspends it: every handler then opens its own, in the tenant schema.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run(ProjectSetupRequestedIntegrationEvent event) {
        ProjectSources.Jira jira = event.sources().jira();
        if (jira == null) return;
        UUID projectId = event.projectId();

        boolean linked = true;
        if (event.runs(SetupStepKeys.JIRA_LINK)) {
            linked = attempt(projectId, SetupStepKeys.JIRA_LINK, () -> {
                requireWriteAccess(event);
                IntegrationConnection connection = connectionFor(event.organizationId(), jira.connectionId());
                saveTarget.handle(new SaveProjectTargetCommand(projectId, connection.getId(), jira.projectKey(),
                        jira.issueTypeName(), event.requestedBy()));
                setup.report(projectId, SetupStepKeys.JIRA_LINK, SetupStepStatus.DONE, null);
            });
            if (!linked && event.runs(SetupStepKeys.JIRA_IMPORT)) {
                setup.report(projectId, SetupStepKeys.JIRA_IMPORT, SetupStepStatus.FAILED, "JIRA_LINK_FAILED");
            }
        }
        if (linked && event.runs(SetupStepKeys.JIRA_IMPORT) && jira.importStories()) {
            attempt(projectId, SetupStepKeys.JIRA_IMPORT, () -> {
                requireWriteAccess(event);
                importStories.handle(new ImportJiraStoriesCommand(projectId, null, event.requestedBy()));
                setup.report(projectId, SetupStepKeys.JIRA_IMPORT, SetupStepStatus.RUNNING, null);
            });
        }
    }

    private void requireWriteAccess(ProjectSetupRequestedIntegrationEvent event) {
        boolean allowed = workspace.callerHasProjectPermission(event.projectId(), event.requestedBy(), "INTEGRATION_WRITE")
                && workspace.callerHasProjectPermission(event.projectId(), event.requestedBy(), "INTEGRATION_SYNC");
        if (!allowed) {
            throw new SetupPermissionDenied();
        }
    }

    /** Runs one step; a failure is reported and logged, and the caller learns whether it worked. */
    private boolean attempt(UUID projectId, String stepKey, Runnable step) {
        try {
            step.run();
            return true;
        } catch (RuntimeException e) {
            log.warn("Setup step {} of project {} failed: {}", stepKey, projectId, e.getMessage());
            setup.report(projectId, stepKey, SetupStepStatus.FAILED, failureDetail(e));
            return false;
        }
    }

    static String failureDetail(RuntimeException e) {
        if (e instanceof DomainException domain) return domain.error().code();
        return e instanceof SetupPermissionDenied ? "INSUFFICIENT_PERMISSIONS" : UNEXPECTED;
    }

    /** The creator no longer holds the permission to link integrations (it was taken away right after). */
    private static final class SetupPermissionDenied extends RuntimeException {
        SetupPermissionDenied() {
            super("The creator cannot link integrations to the project");
        }
    }
}
