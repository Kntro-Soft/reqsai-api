package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.gateway.application.notification.IntegrationJobProgressNotifier;
import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Frames every integration batch execution with the two cross-cutting concerns the engine cannot
 * know about:
 *
 * <ol>
 *   <li><strong>Tenant restoration</strong> — the batch job runs on an executor thread with no
 *       filter-managed {@link TenantContext}. {@code beforeJob} restores the tenant/schema captured
 *       into the job parameters at launch time (the same snapshot-then-restore pattern used by
 *       {@code TenantAwareModuleListener} for async event consumers); {@code afterJob} clears it in a
 *       {@code finally} so the pooled thread never leaks a schema. The whole execution — listeners,
 *       step, readers, processors — runs on this one thread, so every Hibernate session it opens
 *       resolves the caller's tenant schema.</li>
 *   <li><strong>Terminal projection state</strong> — {@code afterJob} runs whether the execution
 *       COMPLETED or FAILED, and is where the domain-facing {@code integration_sync_jobs} row gets
 *       its terminal status, {@code finished_at} and message (fatal-error summary, or the
 *       "N duplicados omitidos" note for imports), followed by the final STOMP publish.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationJobExecutionListener implements JobExecutionListener {

    private final IntegrationSyncJobRepository jobs;
    private final IntegrationJobProgressNotifier progress;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String tenantId = jobExecution.getJobParameters().getString(IntegrationJobParameters.TENANT_ID);
        String tenantSchema = jobExecution.getJobParameters().getString(IntegrationJobParameters.TENANT_SCHEMA);
        TenantContext.setCurrentTenant(tenantId != null ? tenantId : TenantContext.DEFAULT_SCHEMA);
        TenantContext.setCurrentSchema(tenantSchema != null ? tenantSchema : TenantContext.DEFAULT_SCHEMA);
        log.debug("Integration batch job {} running for tenant schema {}",
                jobExecution.getJobInstance().getJobName(), tenantSchema);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            String domainJobId = jobExecution.getJobParameters().getString(IntegrationJobParameters.DOMAIN_JOB_ID);
            IntegrationSyncJob job = domainJobId == null
                    ? null
                    : jobs.findById(UUID.fromString(domainJobId)).orElse(null);
            if (job == null || !job.isRunning()) {
                log.warn("No RUNNING projection row to finalize for batch execution {}", jobExecution.getId());
                return;
            }
            if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                job.complete(completionMessage(job));
            } else {
                job.fail(failureMessage(jobExecution));
            }
            progress.publish(jobs.save(job));
        } finally {
            TenantContext.clear();
        }
    }

    /** Imports report skipped duplicates; other jobs complete silently. */
    private static String completionMessage(IntegrationSyncJob job) {
        int duplicates = job.getProcessed() - job.getSucceeded() - job.getFailed();
        if (job.getJobType() == IntegrationSyncJobType.IMPORT && duplicates > 0) {
            return duplicates + " duplicados omitidos";
        }
        return null;
    }

    /** First failure message of the execution (e.g. Jira unreachable while fetching), token-free. */
    private static String failureMessage(JobExecution jobExecution) {
        return jobExecution.getAllFailureExceptions().stream()
                .map(Throwable::getMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("Job failed with status " + jobExecution.getStatus());
    }
}
