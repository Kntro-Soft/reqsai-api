package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Integration sync job starter (single-running-job rule)")
class IntegrationSyncJobStarterTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private IntegrationSyncJobRepository jobs;
    @InjectMocks
    private IntegrationSyncJobStarter starter;

    @Test
    @DisplayName("persists a RUNNING job when none of the same type is running")
    void starts_when_free() {
        when(jobs.existsRunning(PROJECT, IntegrationSyncJobType.IMPORT)).thenReturn(false);
        when(jobs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IntegrationSyncJob job = starter.start(PROJECT, IntegrationSyncJobType.IMPORT, 3, USER);

        assertThat(job.getStatus()).isEqualTo(IntegrationSyncJobStatus.RUNNING);
        assertThat(job.getProjectId()).isEqualTo(PROJECT);
        assertThat(job.getJobType()).isEqualTo(IntegrationSyncJobType.IMPORT);
        assertThat(job.getTotal()).isEqualTo(3);
        assertThat(job.getRequestedBy()).isEqualTo(USER);
    }

    @Test
    @DisplayName("409 INTEGRATION_JOB_ALREADY_RUNNING when a job of the same type is running")
    void conflicts_on_running_job() {
        when(jobs.existsRunning(PROJECT, IntegrationSyncJobType.PUSH_ALL)).thenReturn(true);

        assertThatThrownBy(() -> starter.start(PROJECT, IntegrationSyncJobType.PUSH_ALL, 0, USER))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error().code())
                        .isEqualTo("INTEGRATION_JOB_ALREADY_RUNNING"));
        verify(jobs, never()).save(any());
    }

    @Test
    @DisplayName("maps the unique-index race (partial index backstop) to the same 409")
    void conflicts_on_racing_insert() {
        when(jobs.existsRunning(PROJECT, IntegrationSyncJobType.IMPORT)).thenReturn(false);
        when(jobs.save(any())).thenThrow(new DataIntegrityViolationException("uq_integration_sync_jobs_running"));

        assertThatThrownBy(() -> starter.start(PROJECT, IntegrationSyncJobType.IMPORT, 0, USER))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error().code())
                        .isEqualTo("INTEGRATION_JOB_ALREADY_RUNNING"));
    }
}
