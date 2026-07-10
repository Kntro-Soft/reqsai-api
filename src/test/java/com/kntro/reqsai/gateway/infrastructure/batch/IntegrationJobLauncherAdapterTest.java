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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch: launcher adapter captures the tenant into job parameters")
class IntegrationJobLauncherAdapterTest {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();

    @Mock
    private JobOperator jobOperator;
    @Mock
    private Job jiraImportJob;
    @Mock
    private Job jiraPushAllJob;
    @Mock
    private IntegrationSyncJobRepository jobs;
    @Mock
    private IntegrationJobProgressNotifier progress;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private IntegrationJobLauncherAdapter adapter() {
        return new IntegrationJobLauncherAdapter(jobOperator, jiraImportJob, jiraPushAllJob, jobs, progress);
    }

    @Test
    @DisplayName("launchImport snapshots the caller's tenant and passes it as job parameters")
    void captures_tenant_snapshot() throws Exception {
        TenantContext.setCurrentTenant("org-1");
        TenantContext.setCurrentSchema("tenant_acme");

        adapter().launchImport(JOB_ID, PROJECT, List.of("PAY-1", "PAY-2"));

        ArgumentCaptor<JobParameters> params = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(jiraImportJob), params.capture());
        JobParameters captured = params.getValue();
        assertThat(captured.getString(IntegrationJobParameters.TENANT_ID)).isEqualTo("org-1");
        assertThat(captured.getString(IntegrationJobParameters.TENANT_SCHEMA)).isEqualTo("tenant_acme");
        assertThat(captured.getString(IntegrationJobParameters.PROJECT_ID)).isEqualTo(PROJECT.toString());
        assertThat(captured.getString(IntegrationJobParameters.ISSUE_KEYS)).isEqualTo("PAY-1,PAY-2");
        // Only the domain job id identifies the JobInstance: one API launch == one fresh instance.
        assertThat(captured.getParameter(IntegrationJobParameters.DOMAIN_JOB_ID).identifying()).isTrue();
        assertThat(captured.getParameter(IntegrationJobParameters.TENANT_SCHEMA).identifying()).isFalse();
    }

    @Test
    @DisplayName("launchPushAll without a selection omits story ids and starts the push-all job")
    void launches_push_all() throws Exception {
        TenantContext.setCurrentTenant("org-1");
        TenantContext.setCurrentSchema("tenant_acme");

        adapter().launchPushAll(JOB_ID, PROJECT, null);

        ArgumentCaptor<JobParameters> params = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(jiraPushAllJob), params.capture());
        assertThat(params.getValue().getParameter(IntegrationJobParameters.STORY_IDS)).isNull();
        assertThat(params.getValue().getParameter(IntegrationJobParameters.ISSUE_KEYS)).isNull();
    }

    @Test
    @DisplayName("launchPushAll with a selection passes the story ids as a comma-joined job parameter")
    void launches_push_all_with_selection() throws Exception {
        TenantContext.setCurrentTenant("org-1");
        TenantContext.setCurrentSchema("tenant_acme");
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        adapter().launchPushAll(JOB_ID, PROJECT, List.of(s1, s2));

        ArgumentCaptor<JobParameters> params = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(jiraPushAllJob), params.capture());
        assertThat(params.getValue().getString(IntegrationJobParameters.STORY_IDS))
                .isEqualTo(s1 + "," + s2);
    }

    @Test
    @DisplayName("a failed launch fails the projection row so no client watches a phantom RUNNING job")
    void fails_projection_when_launch_fails() throws Exception {
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.IMPORT, 0, null);
        when(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new IllegalStateException("no executor"));
        when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobs.save(job)).thenReturn(job);

        assertThatThrownBy(() -> adapter().launchImport(JOB_ID, PROJECT, null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(job.getStatus()).isEqualTo(IntegrationSyncJobStatus.FAILED);
        verify(progress).publish(job);
    }
}
