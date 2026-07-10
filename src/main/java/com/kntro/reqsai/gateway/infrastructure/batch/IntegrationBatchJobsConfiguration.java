package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.notification.IntegrationJobProgressNotifier;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.service.JiraImportService;
import com.kntro.reqsai.gateway.application.service.StoryPushService;
import com.kntro.reqsai.gateway.application.service.StoryPushService.PushContext;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ItemProcessListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The two integration batch jobs (ADR-0023): {@code jiraImportJob} and {@code jiraPushAllJob}. Both
 * follow the same topology — a single <strong>chunk-oriented step</strong> whose reader resolves the
 * work list up front (planning the projection's {@code total}), whose processor delegates one item
 * at a time to the existing application services, and whose writer is a no-op (the services persist
 * their own side effects; the step only orchestrates).
 *
 * <p>Key Spring Batch concepts as used here:
 * <ul>
 *   <li><strong>Chunk-oriented processing</strong> — items are read/processed one by one and the
 *       chunk transaction commits every {@value #CHUNK_SIZE} items, bounding both transaction size
 *       and lost progress on a crash.</li>
 *   <li><strong>Fault tolerance</strong> — {@code faultTolerant().skip(Exception).skipLimit(MAX)}
 *       means one bad item is <em>skipped</em> (counted by the SkipListener), never fatal; that
 *       mirrors the per-item semantics of the old synchronous endpoints. A failure <em>outside</em>
 *       item processing (e.g. Jira unreachable in the reader) still fails the step and the job.</li>
 *   <li><strong>{@code @StepScope}</strong> — readers/processors/progress listener are created per
 *       step <em>execution</em> and parameterized from {@code JobParameters} (late binding), because
 *       singleton step components could not carry per-run state like the project or job id.</li>
 * </ul>
 *
 * <p>Placement: this is infrastructure. The step components only <em>drive</em> the application
 * layer ({@link JiraImportService}, {@link StoryPushService}) — swapping the engine again would
 * touch this package and nothing else.
 */
@Configuration
public class IntegrationBatchJobsConfiguration {

    public static final String IMPORT_JOB_NAME = "jiraImportJob";
    public static final String PUSH_ALL_JOB_NAME = "jiraPushAllJob";

    private static final int CHUNK_SIZE = 5;

    // ==================================
    // IMPORT JOB
    // ==================================

    @Bean
    public Job jiraImportJob(JobRepository jobRepository,
                             @Qualifier("jiraImportStep") Step jiraImportStep,
                             IntegrationJobExecutionListener integrationJobExecutionListener) {
        return new JobBuilder(IMPORT_JOB_NAME, jobRepository)
                .listener(integrationJobExecutionListener)
                .start(jiraImportStep)
                .build();
    }

    @Bean
    public Step jiraImportStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               @Qualifier("jiraImportReader") ListItemReader<RemoteIssue> jiraImportReader,
                               JiraImportItemProcessor jiraImportProcessor,
                               IntegrationJobProgressListener integrationJobProgressListener) {
        return new StepBuilder("jiraImportStep", jobRepository)
                .<RemoteIssue, SyncItemOutcome>chunk(CHUNK_SIZE)
                .reader(jiraImportReader)
                .processor(jiraImportProcessor)
                .writer(chunk -> { })
                .transactionManager(transactionManager)
                .listener((ItemProcessListener<Object, SyncItemOutcome>) integrationJobProgressListener)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(Long.MAX_VALUE)
                .skipListener(integrationJobProgressListener)
                .build();
    }

    /**
     * Resolves the import work list at step start: target → provider context → eligible Jira issues,
     * optionally restricted to the requested keys. Also fixes the projection's {@code total} now that
     * the real item count is known. A Jira failure here is fatal by design (nothing was processed
     * yet) and fails the job.
     */
    @Bean
    @StepScope
    public ListItemReader<RemoteIssue> jiraImportReader(
            @Value("#{jobParameters['" + IntegrationJobParameters.DOMAIN_JOB_ID + "']}") String domainJobId,
            @Value("#{jobParameters['" + IntegrationJobParameters.PROJECT_ID + "']}") String projectId,
            @Value("#{jobParameters['" + IntegrationJobParameters.ISSUE_KEYS + "']}") String issueKeysCsv,
            ProjectIntegrationTargetRepository targets,
            JiraImportService importService,
            IntegrationSyncJobRepository jobs,
            IntegrationJobProgressNotifier progress) {
        UUID project = UUID.fromString(projectId);
        PushContext ctx = importService.contextFor(requireTarget(targets, project));
        Set<String> requested = IntegrationJobParameters.parseIssueKeys(issueKeysCsv);
        List<RemoteIssue> selected = importService.fetchIssues(ctx).stream()
                .filter(issue -> requested.isEmpty() || requested.contains(issue.issueKey()))
                .toList();
        planTotal(jobs, progress, domainJobId, selected.size());
        return new ListItemReader<>(selected);
    }

    @Bean
    @StepScope
    public JiraImportItemProcessor jiraImportProcessor(
            @Value("#{jobParameters['" + IntegrationJobParameters.PROJECT_ID + "']}") String projectId,
            JiraImportService importService) {
        return new JiraImportItemProcessor(importService, UUID.fromString(projectId));
    }

    // ==================================
    // PUSH-ALL JOB
    // ==================================

    @Bean
    public Job jiraPushAllJob(JobRepository jobRepository,
                              @Qualifier("jiraPushAllStep") Step jiraPushAllStep,
                              IntegrationJobExecutionListener integrationJobExecutionListener) {
        return new JobBuilder(PUSH_ALL_JOB_NAME, jobRepository)
                .listener(integrationJobExecutionListener)
                .start(jiraPushAllStep)
                .build();
    }

    @Bean
    public Step jiraPushAllStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                @Qualifier("jiraPushAllReader") ListItemReader<StoryView> jiraPushAllReader,
                                JiraStoryPushItemProcessor jiraStoryPushProcessor,
                                IntegrationJobProgressListener integrationJobProgressListener) {
        return new StepBuilder("jiraPushAllStep", jobRepository)
                .<StoryView, SyncItemOutcome>chunk(CHUNK_SIZE)
                .reader(jiraPushAllReader)
                .processor(jiraStoryPushProcessor)
                .writer(chunk -> { })
                .transactionManager(transactionManager)
                .listener((ItemProcessListener<Object, SyncItemOutcome>) integrationJobProgressListener)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(Long.MAX_VALUE)
                .skipListener(integrationJobProgressListener)
                .build();
    }

    /** Resolves the push work list (every project story) and fixes the projection's {@code total}. */
    @Bean
    @StepScope
    public ListItemReader<StoryView> jiraPushAllReader(
            @Value("#{jobParameters['" + IntegrationJobParameters.DOMAIN_JOB_ID + "']}") String domainJobId,
            @Value("#{jobParameters['" + IntegrationJobParameters.PROJECT_ID + "']}") String projectId,
            DiscoveryStoryReadPort stories,
            IntegrationSyncJobRepository jobs,
            IntegrationJobProgressNotifier progress) {
        List<StoryView> all = stories.listStories(UUID.fromString(projectId));
        planTotal(jobs, progress, domainJobId, all.size());
        return new ListItemReader<>(all);
    }

    @Bean
    @StepScope
    public JiraStoryPushItemProcessor jiraStoryPushProcessor(
            @Value("#{jobParameters['" + IntegrationJobParameters.PROJECT_ID + "']}") String projectId,
            ProjectIntegrationTargetRepository targets,
            StoryPushService pushService) {
        UUID project = UUID.fromString(projectId);
        return new JiraStoryPushItemProcessor(pushService,
                pushService.contextFor(requireTarget(targets, project)));
    }

    // ==================================
    // SHARED STEP COMPONENTS
    // ==================================

    @Bean
    @StepScope
    public IntegrationJobProgressListener integrationJobProgressListener(
            @Value("#{jobParameters['" + IntegrationJobParameters.DOMAIN_JOB_ID + "']}") String domainJobId,
            IntegrationSyncJobRepository jobs,
            IntegrationJobProgressNotifier progress) {
        return new IntegrationJobProgressListener(UUID.fromString(domainJobId), jobs, progress);
    }

    private static ProjectIntegrationTarget requireTarget(ProjectIntegrationTargetRepository targets, UUID projectId) {
        return targets.findByProjectId(projectId)
                .orElseThrow(() -> IntegrationsExceptions.targetNotConfigured(projectId));
    }

    private static void planTotal(IntegrationSyncJobRepository jobs, IntegrationJobProgressNotifier progress,
                                  String domainJobId, int total) {
        jobs.findById(UUID.fromString(domainJobId)).filter(IntegrationSyncJob::isRunning).ifPresent(job -> {
            job.planTotal(total);
            progress.publish(jobs.save(job));
        });
    }
}
