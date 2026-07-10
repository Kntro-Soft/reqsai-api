package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.gateway.application.notification.IntegrationJobProgressNotifier;
import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch: integration job execution listener (tenant framing + terminal projection)")
class IntegrationJobExecutionListenerTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private IntegrationSyncJobRepository jobs;
    @Mock
    private IntegrationJobProgressNotifier progress;
    @InjectMocks
    private IntegrationJobExecutionListener listener;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("beforeJob restores the tenant captured into the job parameters")
    void restores_tenant() {
        listener.beforeJob(execution(IntegrationSyncJobType.IMPORT));

        assertThat(TenantContext.getCurrentTenant()).isEqualTo("org-1");
        assertThat(TenantContext.getCurrentSchema()).isEqualTo("tenant_acme");
    }

    @Test
    @DisplayName("afterJob COMPLETED completes the projection, notes skipped duplicates and publishes")
    void completes_with_duplicates_note() {
        IntegrationSyncJob job = runningJob(IntegrationSyncJobType.IMPORT);
        job.planTotal(3);
        job.recordSuccess();
        job.recordSkipped();
        job.recordSkipped();
        when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobs.save(job)).thenReturn(job);
        JobExecution execution = execution(IntegrationSyncJobType.IMPORT);
        execution.setStatus(BatchStatus.COMPLETED);

        listener.afterJob(execution);

        assertThat(job.getStatus()).isEqualTo(IntegrationSyncJobStatus.COMPLETED);
        assertThat(job.getMessage()).isEqualTo("2 duplicados omitidos");
        assertThat(job.getFinishedAt()).isNotNull();
        verify(progress).publish(job);
        assertThat(TenantContext.getCurrentSchema()).isNull();
    }

    @Test
    @DisplayName("afterJob COMPLETED without duplicates leaves the message null")
    void completes_silently() {
        IntegrationSyncJob job = runningJob(IntegrationSyncJobType.PUSH_ALL);
        job.planTotal(1);
        job.recordSuccess();
        when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobs.save(job)).thenReturn(job);
        JobExecution execution = execution(IntegrationSyncJobType.PUSH_ALL);
        execution.setStatus(BatchStatus.COMPLETED);

        listener.afterJob(execution);

        assertThat(job.getStatus()).isEqualTo(IntegrationSyncJobStatus.COMPLETED);
        assertThat(job.getMessage()).isNull();
    }

    @Test
    @DisplayName("afterJob FAILED fails the projection with the execution's failure message")
    void fails_with_message() {
        IntegrationSyncJob job = runningJob(IntegrationSyncJobType.IMPORT);
        when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobs.save(job)).thenReturn(job);
        JobExecution execution = execution(IntegrationSyncJobType.IMPORT);
        execution.setStatus(BatchStatus.FAILED);
        execution.addFailureException(new IllegalStateException("Jira unreachable"));

        listener.afterJob(execution);

        assertThat(job.getStatus()).isEqualTo(IntegrationSyncJobStatus.FAILED);
        assertThat(job.getMessage()).isEqualTo("Jira unreachable");
        verify(progress).publish(job);
        assertThat(TenantContext.getCurrentSchema()).isNull();
    }

    @Test
    @DisplayName("afterJob clears the tenant even when no RUNNING projection row exists")
    void clears_tenant_without_row() {
        when(jobs.findById(JOB_ID)).thenReturn(Optional.empty());
        JobExecution execution = execution(IntegrationSyncJobType.IMPORT);
        execution.setStatus(BatchStatus.COMPLETED);
        listener.beforeJob(execution);

        listener.afterJob(execution);

        verify(jobs, never()).save(any());
        assertThat(TenantContext.getCurrentSchema()).isNull();
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    private static IntegrationSyncJob runningJob(IntegrationSyncJobType type) {
        return new IntegrationSyncJob(PROJECT, type, 0, UUID.randomUUID());
    }

    private static JobExecution execution(IntegrationSyncJobType type) {
        String jobName = type == IntegrationSyncJobType.IMPORT ? "jiraImportJob" : "jiraPushAllJob";
        return new JobExecution(1L, new JobInstance(1L, jobName), new JobParametersBuilder()
                .addString(IntegrationJobParameters.DOMAIN_JOB_ID, JOB_ID.toString(), true)
                .addString(IntegrationJobParameters.PROJECT_ID, PROJECT.toString(), false)
                .addString(IntegrationJobParameters.TENANT_ID, "org-1", false)
                .addString(IntegrationJobParameters.TENANT_SCHEMA, "tenant_acme", false)
                .toJobParameters());
    }
}
