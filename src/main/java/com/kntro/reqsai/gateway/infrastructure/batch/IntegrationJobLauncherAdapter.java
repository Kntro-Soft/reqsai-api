package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.gateway.application.notification.IntegrationJobProgressNotifier;
import com.kntro.reqsai.gateway.application.port.IntegrationJobLauncher;
import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext.TenantSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Spring Batch adapter for the {@link IntegrationJobLauncher} port. Captures the caller's tenant on
 * the request thread ({@link TenantContext#capture()}) into <em>job parameters</em> — the batch
 * equivalent of the snapshot-then-restore pattern used by the app's other async paths — and starts
 * the job through the {@code JobOperator}, whose task executor makes {@code start} return as soon as
 * the execution is registered (that is what backs the {@code 202 Accepted} contract).
 *
 * <p>The {@code domainJobId} is the only <strong>identifying</strong> parameter, so each API launch
 * is a brand-new JobInstance. If the launch itself fails (no executor thread, metadata store down),
 * the projection row is failed immediately so no client is left watching a phantom RUNNING job.
 */
@Component
@Slf4j
public class IntegrationJobLauncherAdapter implements IntegrationJobLauncher {

    private final JobOperator jobOperator;
    private final Job jiraImportJob;
    private final Job jiraPushAllJob;
    private final IntegrationSyncJobRepository jobs;
    private final IntegrationJobProgressNotifier progress;

    public IntegrationJobLauncherAdapter(
            JobOperator jobOperator,
            @Qualifier("jiraImportJob") Job jiraImportJob,
            @Qualifier("jiraPushAllJob") Job jiraPushAllJob,
            IntegrationSyncJobRepository jobs,
            IntegrationJobProgressNotifier progress) {
        this.jobOperator = jobOperator;
        this.jiraImportJob = jiraImportJob;
        this.jiraPushAllJob = jiraPushAllJob;
        this.jobs = jobs;
        this.progress = progress;
    }

    @Override
    public void launchImport(UUID jobId, UUID projectId, @Nullable List<String> issueKeys) {
        launch(jiraImportJob, jobId, projectId,
                IntegrationJobParameters.ISSUE_KEYS, IntegrationJobParameters.joinIssueKeys(issueKeys));
    }

    @Override
    public void launchPushAll(UUID jobId, UUID projectId, @Nullable List<UUID> storyIds) {
        launch(jiraPushAllJob, jobId, projectId,
                IntegrationJobParameters.STORY_IDS, IntegrationJobParameters.joinStoryIds(storyIds));
    }

    private void launch(Job batchJob, UUID jobId, UUID projectId,
                        String selectionKey, @Nullable String selectionCsv) {
        TenantSnapshot tenant = TenantContext.capture();
        JobParametersBuilder params = new JobParametersBuilder()
                .addString(IntegrationJobParameters.DOMAIN_JOB_ID, jobId.toString(), true)
                .addString(IntegrationJobParameters.PROJECT_ID, projectId.toString(), false)
                .addString(IntegrationJobParameters.TENANT_ID, tenant.tenantId(), false)
                .addString(IntegrationJobParameters.TENANT_SCHEMA, tenant.tenantSchema(), false);
        if (selectionCsv != null) {
            params.addString(selectionKey, selectionCsv, false);
        }
        try {
            jobOperator.start(batchJob, params.toJobParameters());
        } catch (Exception e) {
            failUnlaunched(jobId, e);
        }
    }

    /** The execution never started: fail the projection so the UI is not stuck on RUNNING. */
    private void failUnlaunched(UUID jobId, Exception cause) {
        log.error("Could not launch integration batch job {}", jobId, cause);
        jobs.findById(jobId).filter(IntegrationSyncJob::isRunning).ifPresent(job -> {
            job.fail("The background job could not be launched");
            progress.publish(jobs.save(job));
        });
        throw new IllegalStateException("Could not launch integration batch job " + jobId, cause);
    }
}
