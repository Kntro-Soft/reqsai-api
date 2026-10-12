package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.command.ConnectRepositoryCommand;
import com.kntro.reqsai.codebase.application.handler.ConnectRepositoryCommandHandler;
import com.kntro.reqsai.codebase.application.handler.ReindexRepositoryCommandHandler;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: connecting a new project's repositories")
class ProjectCodeSetupTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ConnectRepositoryCommandHandler connect;
    @Mock
    private ReindexRepositoryCommandHandler reindex;
    @Mock
    private CodeRepositoryRepository repositories;
    @Mock
    private WorkspaceModuleApi workspace;
    @Mock
    private ProjectSetupReporter setup;
    @InjectMocks
    private ProjectCodeSetup code;

    private static ProjectSetupRequestedIntegrationEvent event(String... repositories) {
        List<ProjectSources.GitHub> github = java.util.Arrays.stream(repositories)
                .map(r -> new ProjectSources.GitHub(r, null, null)).toList();
        return ProjectSetupRequestedIntegrationEvent.of(ORG, PROJECT, USER, new ProjectSources(null, github),
                List.of(SetupStepKeys.GITHUB_REPOS));
    }

    @Test
    @DisplayName("connects every repository on behalf of the creator and reports the step running")
    void connectsAll() {
        when(workspace.callerHasProjectPermission(PROJECT, USER, "INTEGRATION_WRITE")).thenReturn(true);

        code.run(event("acme/a", "acme/b"));

        verify(connect).handle(new ConnectRepositoryCommand(PROJECT, "acme/a", null, null));
        verify(connect).handle(new ConnectRepositoryCommand(PROJECT, "acme/b", null, null));
        verify(setup).report(PROJECT, SetupStepKeys.GITHUB_REPOS, SetupStepStatus.RUNNING, null);
    }

    @Test
    @DisplayName("a repository that fails is recorded and does not stop the others")
    void oneFailureKeepsGoing() {
        when(workspace.callerHasProjectPermission(PROJECT, USER, "INTEGRATION_WRITE")).thenReturn(true);
        when(connect.handle(new ConnectRepositoryCommand(PROJECT, "acme/a", null, null)))
                .thenThrow(CodebaseExceptions.notFound("acme/a"));

        code.run(event("acme/a", "acme/b"));

        verify(connect).handle(new ConnectRepositoryCommand(PROJECT, "acme/b", null, null));
        verify(setup).report(PROJECT, SetupStepKeys.GITHUB_REPOS, SetupStepStatus.RUNNING,
                "acme/a: CODE_REPOSITORY_NOT_FOUND");
    }

    @Test
    @DisplayName("the step fails, without throwing, when no repository could be connected")
    void allFail() {
        when(workspace.callerHasProjectPermission(PROJECT, USER, "INTEGRATION_WRITE")).thenReturn(true);
        when(connect.handle(any())).thenThrow(new IllegalStateException("boom"));

        code.run(event("acme/a"));

        verify(setup).report(PROJECT, SetupStepKeys.GITHUB_REPOS, SetupStepStatus.FAILED,
                "acme/a: " + ProjectCodeSetup.UNEXPECTED);
    }

    @Test
    @DisplayName("a creator who lost the permission connects nothing")
    void permissionChecked() {
        when(workspace.callerHasProjectPermission(PROJECT, USER, "INTEGRATION_WRITE")).thenReturn(false);

        code.run(event("acme/a"));

        verify(connect, never()).handle(any());
        verify(setup).report(PROJECT, SetupStepKeys.GITHUB_REPOS, SetupStepStatus.FAILED, "INSUFFICIENT_PERMISSIONS");
    }

    @Test
    @DisplayName("does nothing when the event does not ask for the repositories step")
    void ignoresOtherSteps() {
        var jiraOnly = ProjectSetupRequestedIntegrationEvent.of(ORG, PROJECT, USER,
                new ProjectSources(null, List.of(new ProjectSources.GitHub("acme/a", null, null))),
                List.of(SetupStepKeys.JIRA_LINK));

        code.run(jiraOnly);

        verify(connect, never()).handle(any());
        verify(setup, never()).report(any(), any(), any(), any());
        verify(reindex, never()).handle(any());
        verify(repositories, never()).findAllByProjectId(eq(PROJECT));
        verify(workspace, never()).callerHasProjectPermission(any(), any(), any());
    }
}
