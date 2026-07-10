package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.notification.IntegrationJobProgressNotifier;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.result.ImportStoryResult;
import com.kntro.reqsai.gateway.application.service.StoryPushService.PushContext;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext.TenantSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Executes Jira import / push-all jobs on the shared async executor, off the request thread. The
 * request thread only creates the job row and calls {@code dispatch*}; the worker then reuses the
 * existing per-item mechanics ({@link JiraImportService} / {@link StoryPushService}), persisting the
 * job counters after every item and mirroring each update to STOMP via
 * {@link IntegrationJobProgressNotifier}.
 *
 * <p><strong>Tenant propagation</strong> — the tenant schema lives in the {@link TenantContext}
 * ThreadLocal, which the async thread does not inherit. Following the established pattern for
 * threads with no filter-managed context (see {@code TenantContext.runWith} and
 * {@code TenantAwareModuleListener}), {@code dispatch*} captures a {@link TenantSnapshot} on the
 * request thread and the worker restores it with {@link TenantContext#runWith} before any DB access,
 * so every Hibernate session the job opens resolves the caller's tenant schema.
 *
 * <p>Failure semantics: per-item failures increment {@code failed} and the run continues; a fatal
 * error (target/connection resolution, Jira fetch) marks the job {@code FAILED} with a bounded
 * message. Import duplicates count toward {@code processed} only and are summarized in the terminal
 * message.
 */
@Component
@Slf4j
public class IntegrationSyncJobRunner {

    private final TaskExecutor executor;
    private final IntegrationSyncJobRepository jobs;
    private final ProjectIntegrationTargetRepository targets;
    private final JiraImportService importService;
    private final StoryPushService pushService;
    private final DiscoveryStoryReadPort stories;
    private final IntegrationJobProgressNotifier progress;

    public IntegrationSyncJobRunner(
            @Qualifier("taskExecutor") TaskExecutor executor,
            IntegrationSyncJobRepository jobs,
            ProjectIntegrationTargetRepository targets,
            JiraImportService importService,
            StoryPushService pushService,
            DiscoveryStoryReadPort stories,
            IntegrationJobProgressNotifier progress) {
        this.executor = executor;
        this.jobs = jobs;
        this.targets = targets;
        this.importService = importService;
        this.pushService = pushService;
        this.stories = stories;
        this.progress = progress;
    }

    /** Dispatches an import run, capturing the caller's tenant for the worker thread. */
    public void dispatchImport(UUID jobId, @Nullable List<String> issueKeys) {
        TenantSnapshot tenant = TenantContext.capture();
        executor.execute(() -> TenantContext.runWith(tenant, () -> runImport(jobId, issueKeys)));
    }

    /** Dispatches a push-all run, capturing the caller's tenant for the worker thread. */
    public void dispatchPushAll(UUID jobId) {
        TenantSnapshot tenant = TenantContext.capture();
        executor.execute(() -> TenantContext.runWith(tenant, () -> runPushAll(jobId)));
    }

    private void runImport(UUID jobId, @Nullable List<String> issueKeys) {
        IntegrationSyncJob job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Import job {} vanished before the worker started", jobId);
            return;
        }
        try {
            PushContext ctx = importService.contextFor(requireTarget(job.getProjectId()));
            List<RemoteIssue> issues = importService.fetchIssues(ctx);

            Set<String> requested = issueKeys == null ? Set.of() : Set.copyOf(issueKeys);
            List<RemoteIssue> selected = issues.stream()
                    .filter(issue -> requested.isEmpty() || requested.contains(issue.issueKey()))
                    .toList();
            job.planTotal(selected.size());
            saveAndPublish(job);

            int duplicates = 0;
            for (RemoteIssue issue : selected) {
                // importIssue captures per-issue failures itself, so one bad issue never aborts the run.
                ImportStoryResult result = importService.importIssue(job.getProjectId(), issue);
                switch (result.status()) {
                    case IMPORTED -> job.recordSuccess();
                    case FAILED -> job.recordFailure();
                    case DUPLICATE -> {
                        job.recordSkipped();
                        duplicates++;
                    }
                }
                saveAndPublish(job);
            }
            job.complete(duplicates > 0 ? duplicates + " duplicados omitidos" : null);
            saveAndPublish(job);
            log.info("Import job {} completed: {}/{} succeeded, {} failed, {} duplicates",
                    jobId, job.getSucceeded(), job.getTotal(), job.getFailed(), duplicates);
        } catch (RuntimeException e) {
            failJob(job, e);
        }
    }

    private void runPushAll(UUID jobId) {
        IntegrationSyncJob job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Push-all job {} vanished before the worker started", jobId);
            return;
        }
        try {
            PushContext ctx = pushService.contextFor(requireTarget(job.getProjectId()));
            List<StoryView> all = stories.listStories(job.getProjectId());
            job.planTotal(all.size());
            saveAndPublish(job);

            for (StoryView story : all) {
                try {
                    pushService.push(ctx, story);
                    job.recordSuccess();
                } catch (DomainException e) {
                    // One story's provider failure must not abort the rest of the batch.
                    log.warn("Push failed for story {} [{}]", story.storyId(), e.error().code());
                    job.recordFailure();
                }
                saveAndPublish(job);
            }
            job.complete(null);
            saveAndPublish(job);
            log.info("Push-all job {} completed: {}/{} succeeded, {} failed",
                    jobId, job.getSucceeded(), job.getTotal(), job.getFailed());
        } catch (RuntimeException e) {
            failJob(job, e);
        }
    }

    private ProjectIntegrationTarget requireTarget(UUID projectId) {
        return targets.findByProjectId(projectId)
                .orElseThrow(() -> IntegrationsExceptions.targetNotConfigured(projectId));
    }

    private void saveAndPublish(IntegrationSyncJob job) {
        progress.publish(jobs.save(job));
    }

    /** Terminal fatal path: persist FAILED + message and publish, never letting the worker throw. */
    private void failJob(IntegrationSyncJob job, RuntimeException cause) {
        log.error("Integration job {} failed fatally", job.getId(), cause);
        try {
            job.fail(cause.getMessage());
            saveAndPublish(job);
        } catch (RuntimeException e) {
            log.error("Could not persist failure of integration job {}", job.getId(), e);
        }
    }
}
