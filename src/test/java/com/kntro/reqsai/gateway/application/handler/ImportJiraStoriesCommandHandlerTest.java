package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.ImportJiraStoriesCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationJobLauncher;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.service.IntegrationSyncJobStarter;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Import Jira stories command handler (async job)")
class ImportJiraStoriesCommandHandlerTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ProjectIntegrationTargetRepository targets;
    @Mock
    private IntegrationSyncJobStarter starter;
    @Mock
    private IntegrationJobLauncher launcher;
    @InjectMocks
    private ImportJiraStoriesCommandHandler handler;

    @Test
    @DisplayName("creates a RUNNING job and dispatches the async import")
    void starts_job_and_launches() {
        stubTarget();
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.IMPORT, 2, USER);
        when(starter.start(PROJECT, IntegrationSyncJobType.IMPORT, 2, USER)).thenReturn(job);

        IntegrationSyncJob result = handler.handle(
                new ImportJiraStoriesCommand(PROJECT, List.of("PAY-1", "PAY-2"), USER));

        assertThat(result.getStatus()).isEqualTo(IntegrationSyncJobStatus.RUNNING);
        assertThat(result.getTotal()).isEqualTo(2);
        verify(launcher).launchImport(job.getId(), PROJECT, List.of("PAY-1", "PAY-2"));
    }

    @Test
    @DisplayName("a full import (no issueKeys) starts with total 0 until the worker resolves it")
    void full_import_starts_with_unknown_total() {
        stubTarget();
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.IMPORT, 0, USER);
        when(starter.start(PROJECT, IntegrationSyncJobType.IMPORT, 0, USER)).thenReturn(job);

        IntegrationSyncJob result = handler.handle(new ImportJiraStoriesCommand(PROJECT, null, USER));

        assertThat(result.getTotal()).isZero();
        verify(launcher).launchImport(job.getId(), PROJECT, null);
    }

    @Test
    @DisplayName("409 INTEGRATION_TARGET_NOT_CONFIGURED when no target exists; nothing is launched")
    void no_target_conflicts() {
        when(targets.findByProjectId(PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ImportJiraStoriesCommand(PROJECT, null, USER)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error().code())
                        .isEqualTo("INTEGRATION_TARGET_NOT_CONFIGURED"));
        verify(launcher, never()).launchImport(any(), any(), any());
    }

    @Test
    @DisplayName("propagates the starter's 409 INTEGRATION_JOB_ALREADY_RUNNING without launching")
    void running_job_conflicts() {
        stubTarget();
        when(starter.start(PROJECT, IntegrationSyncJobType.IMPORT, 0, USER))
                .thenThrow(com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions
                        .jobAlreadyRunning(PROJECT, IntegrationSyncJobType.IMPORT.name()));

        assertThatThrownBy(() -> handler.handle(new ImportJiraStoriesCommand(PROJECT, null, USER)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error().code())
                        .isEqualTo("INTEGRATION_JOB_ALREADY_RUNNING"));
        verify(launcher, never()).launchImport(any(), any(), any());
    }

    private void stubTarget() {
        when(targets.findByProjectId(PROJECT)).thenReturn(Optional.of(mock(ProjectIntegrationTarget.class)));
    }
}
