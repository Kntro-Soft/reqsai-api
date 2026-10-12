package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.api.ProjectSetupParticipant;
import com.kntro.reqsai.workspace.api.ProjectSetupRequestedIntegrationEvent;
import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.application.notification.ProjectSetupNotifier;
import com.kntro.reqsai.workspace.application.port.ProjectSetupStepRepository;
import com.kntro.reqsai.workspace.application.result.ProjectSetup;
import com.kntro.reqsai.workspace.domain.model.ProjectSetupStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: project setup")
class ProjectSetupServiceTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final ProjectSources.Jira JIRA = new ProjectSources.Jira(null, "PAY", "Story", true);
    private static final ProjectSources.GitHub REPO = new ProjectSources.GitHub("acme/reservas", null, null);

    @Mock
    private ProjectSetupStepRepository steps;
    @Mock
    private ProjectSetupViews views;
    @Mock
    private ProjectSetupNotifier notifier;
    @Mock
    private ObjectProvider<ProjectSetupParticipant> participants;
    @Mock
    private ApplicationEventPublisher events;

    private ProjectSetupService service() {
        return new ProjectSetupService(steps, views, notifier, participants, events);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a project with no sources records and announces nothing")
    void nothingRequested() {
        service().request(ORG, PROJECT, USER, ProjectSources.none());

        verify(steps, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("records one step per requested source and announces them once")
    void recordsAndAnnounces() {
        ProjectSources sources = new ProjectSources(JIRA, List.of(REPO));

        service().request(ORG, PROJECT, USER, sources);

        ArgumentCaptor<ProjectSetupStep> saved = ArgumentCaptor.forClass(ProjectSetupStep.class);
        verify(steps, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ProjectSetupStep::getStepKey).containsExactly(
                SetupStepKeys.JIRA_LINK, SetupStepKeys.JIRA_IMPORT, SetupStepKeys.GITHUB_REPOS);
        assertThat(saved.getAllValues()).allSatisfy(step -> {
            assertThat(step.getStatus()).isEqualTo(SetupStepStatus.PENDING);
            assertThat(step.getRequest()).isEqualTo(sources);
        });
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(ProjectSetupRequestedIntegrationEvent.class, e -> {
            assertThat(e.projectId()).isEqualTo(PROJECT);
            assertThat(e.requestedBy()).isEqualTo(USER);
            assertThat(e.steps()).containsExactly(
                    SetupStepKeys.JIRA_LINK, SetupStepKeys.JIRA_IMPORT, SetupStepKeys.GITHUB_REPOS);
        });
    }

    @Test
    @DisplayName("without the import toggle there is no import step")
    void noImportStep() {
        service().request(ORG, PROJECT, USER, new ProjectSources(
                new ProjectSources.Jira(null, "PAY", "Story", false), List.of()));

        ArgumentCaptor<ProjectSetupStep> saved = ArgumentCaptor.forClass(ProjectSetupStep.class);
        verify(steps).save(saved.capture());
        assertThat(saved.getValue().getStepKey()).isEqualTo(SetupStepKeys.JIRA_LINK);
    }

    @Test
    @DisplayName("every source module may refuse the sources before the project exists")
    void validateAsksEveryParticipant() {
        ProjectSetupParticipant refusing = new ProjectSetupParticipant() {
            @Override
            public void validate(ProjectSources sources, UUID requestedBy) {
                throw new DomainException(com.kntro.reqsai.workspace.domain.exception.WorkspaceError.PROJECT_NOT_FOUND,
                        "refused");
            }
        };
        when(participants.orderedStream()).thenReturn(Stream.of(refusing));

        assertThatThrownBy(() -> service().validate(new ProjectSources(null, List.of(REPO)), USER))
                .isInstanceOf(DomainException.class).hasMessage("refused");
        service().validate(ProjectSources.none(), USER);
    }

    @Test
    @DisplayName("a failed Jira link is retried together with the import that waited on it")
    void retryLinkAndImport() {
        TenantContext.setCurrentTenant(ORG.toString());
        ProjectSources sources = new ProjectSources(JIRA, List.of());
        ProjectSetupStep link = new ProjectSetupStep(PROJECT, SetupStepKeys.JIRA_LINK, sources);
        link.report(SetupStepStatus.FAILED, "INTEGRATION_CONNECTION_NOT_FOUND");
        ProjectSetupStep importStep = new ProjectSetupStep(PROJECT, SetupStepKeys.JIRA_IMPORT, sources);
        importStep.report(SetupStepStatus.FAILED, "JIRA_LINK_FAILED");
        when(steps.findByProjectIdAndStepKey(PROJECT, SetupStepKeys.JIRA_LINK)).thenReturn(Optional.of(link));
        when(steps.findByProjectIdAndStepKey(PROJECT, SetupStepKeys.JIRA_IMPORT)).thenReturn(Optional.of(importStep));
        when(views.of(PROJECT)).thenReturn(new ProjectSetup(PROJECT, List.of(
                new com.kntro.reqsai.workspace.api.SetupStepView(SetupStepKeys.JIRA_LINK, SetupStepStatus.FAILED, 0, 0,
                        "INTEGRATION_CONNECTION_NOT_FOUND")), null));

        service().retry(PROJECT, SetupStepKeys.JIRA_LINK, USER);

        assertThat(link.getStatus()).isEqualTo(SetupStepStatus.PENDING);
        assertThat(importStep.getStatus()).isEqualTo(SetupStepStatus.PENDING);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(ProjectSetupRequestedIntegrationEvent.class, e -> {
            assertThat(e.organizationId()).isEqualTo(ORG);
            assertThat(e.steps()).containsExactly(SetupStepKeys.JIRA_LINK, SetupStepKeys.JIRA_IMPORT);
        });
    }

    @Test
    @DisplayName("only a failed step can be retried")
    void retryNeedsAFailure() {
        ProjectSetupStep running = new ProjectSetupStep(PROJECT, SetupStepKeys.GITHUB_REPOS,
                new ProjectSources(null, List.of(REPO)));
        when(steps.findByProjectIdAndStepKey(PROJECT, SetupStepKeys.GITHUB_REPOS)).thenReturn(Optional.of(running));
        when(views.of(PROJECT)).thenReturn(new ProjectSetup(PROJECT, List.of(
                new com.kntro.reqsai.workspace.api.SetupStepView(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.RUNNING,
                        1, 3, null)), null));

        assertThatThrownBy(() -> service().retry(PROJECT, SetupStepKeys.GITHUB_REPOS, USER))
                .isInstanceOf(DomainException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("skipping marks the step skipped and pushes the setup")
    void skip() {
        ProjectSetupStep step = new ProjectSetupStep(PROJECT, SetupStepKeys.JIRA_IMPORT, ProjectSources.none());
        when(steps.findByProjectIdAndStepKey(PROJECT, SetupStepKeys.JIRA_IMPORT)).thenReturn(Optional.of(step));

        service().skip(PROJECT, SetupStepKeys.JIRA_IMPORT);

        assertThat(step.getStatus()).isEqualTo(SetupStepStatus.SKIPPED);
        verify(notifier).changed(PROJECT);
    }
}
