package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.command.ImportJiraStoriesCommand;
import com.kntro.reqsai.gateway.application.command.SaveProjectTargetCommand;
import com.kntro.reqsai.gateway.application.handler.ImportJiraStoriesCommandHandler;
import com.kntro.reqsai.gateway.application.handler.SaveProjectTargetCommandHandler;
import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.api.ProjectSetupReporter;
import com.kntro.reqsai.workspace.api.ProjectSetupRequestedIntegrationEvent;
import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: linking a new project to Jira")
class ProjectJiraSetupTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private IntegrationConnectionRepository connections;
    @Mock
    private SaveProjectTargetCommandHandler saveTarget;
    @Mock
    private ImportJiraStoriesCommandHandler importStories;
    @Mock
    private WorkspaceModuleApi workspace;
    @Mock
    private ProjectSetupReporter setup;
    @InjectMocks
    private ProjectJiraSetup jira;

    private static IntegrationConnection connection() {
        return new IntegrationConnection(ORG, IntegrationProviderType.JIRA, "https://acme.atlassian.net",
                "pm@acme.com", "tok", Instant.now());
    }

    private static ProjectSetupRequestedIntegrationEvent event(boolean importStories, String... steps) {
        return ProjectSetupRequestedIntegrationEvent.of(ORG, PROJECT, USER, new ProjectSources(
                new ProjectSources.Jira(null, "PAY", "Story", importStories), List.of()), List.of(steps));
    }

    private void allowed() {
        when(workspace.callerHasProjectPermission(PROJECT, USER, "INTEGRATION_WRITE")).thenReturn(true);
        when(workspace.callerHasProjectPermission(PROJECT, USER, "INTEGRATION_SYNC")).thenReturn(true);
    }

    @Test
    @DisplayName("falls back to the organization's active Jira connection")
    void defaultConnection() {
        IntegrationConnection active = connection();
        when(connections.findAllByOrganizationId(ORG)).thenReturn(List.of(active));

        assertThat(jira.connectionFor(ORG, null)).isSameAs(active);
    }

    @Test
    @DisplayName("404 when the organization has no Jira connection, or the one named is not its own")
    void connectionMissing() {
        when(connections.findAllByOrganizationId(ORG)).thenReturn(List.of());
        UUID other = UUID.randomUUID();
        when(connections.findByIdAndOrganizationId(other, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jira.connectionFor(ORG, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> jira.connectionFor(ORG, other)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("saves the target, reports the link done and starts the import, as the creator")
    void linksAndImports() {
        allowed();
        IntegrationConnection active = connection();
        when(connections.findAllByOrganizationId(ORG)).thenReturn(List.of(active));

        jira.run(event(true, SetupStepKeys.JIRA_LINK, SetupStepKeys.JIRA_IMPORT));

        verify(saveTarget).handle(new SaveProjectTargetCommand(PROJECT, active.getId(), "PAY", "Story", USER));
        verify(importStories).handle(new ImportJiraStoriesCommand(PROJECT, null, USER));
        verify(setup).report(PROJECT, SetupStepKeys.JIRA_LINK, SetupStepStatus.DONE, null);
        verify(setup).report(PROJECT, SetupStepKeys.JIRA_IMPORT, SetupStepStatus.RUNNING, null);
    }

    @Test
    @DisplayName("a failed link is recorded, the import that waited on it fails with it, and nothing is thrown")
    void linkFailure() {
        allowed();
        when(connections.findAllByOrganizationId(ORG)).thenReturn(List.of());

        jira.run(event(true, SetupStepKeys.JIRA_LINK, SetupStepKeys.JIRA_IMPORT));

        verify(setup).report(PROJECT, SetupStepKeys.JIRA_LINK, SetupStepStatus.FAILED,
                "INTEGRATION_CONNECTION_NOT_FOUND");
        verify(setup).report(PROJECT, SetupStepKeys.JIRA_IMPORT, SetupStepStatus.FAILED, "JIRA_LINK_FAILED");
        verify(importStories, never()).handle(any());
    }

    @Test
    @DisplayName("retrying only the import does not touch the link")
    void importOnly() {
        allowed();

        jira.run(event(true, SetupStepKeys.JIRA_IMPORT));

        verify(saveTarget, never()).handle(any());
        verify(importStories).handle(new ImportJiraStoriesCommand(PROJECT, null, USER));
    }

    @Test
    @DisplayName("an import that cannot start (one already running) is reported failed")
    void importFailure() {
        allowed();
        when(importStories.handle(any())).thenThrow(IntegrationsExceptions.jobAlreadyRunning(PROJECT, "IMPORT"));

        jira.run(event(true, SetupStepKeys.JIRA_IMPORT));

        verify(setup).report(PROJECT, SetupStepKeys.JIRA_IMPORT, SetupStepStatus.FAILED,
                "INTEGRATION_JOB_ALREADY_RUNNING");
    }

    @Test
    @DisplayName("a creator who lost the permission links nothing")
    void permissionChecked() {
        when(workspace.callerHasProjectPermission(PROJECT, USER, "INTEGRATION_WRITE")).thenReturn(false);

        jira.run(event(false, SetupStepKeys.JIRA_LINK));

        verify(saveTarget, never()).handle(any());
        verify(setup).report(PROJECT, SetupStepKeys.JIRA_LINK, SetupStepStatus.FAILED, "INSUFFICIENT_PERMISSIONS");
    }
}
