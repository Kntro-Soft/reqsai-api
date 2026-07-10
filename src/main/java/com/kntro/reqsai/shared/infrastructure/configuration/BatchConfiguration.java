package com.kntro.reqsai.shared.infrastructure.configuration;

import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

/**
 * Spring Batch runtime for the async integration sync jobs (ADR-0023). Extending
 * {@link JdbcDefaultBatchConfiguration} opts into <strong>durable</strong> batch metadata (Spring
 * Batch 6 / Boot 4 default to an in-memory "resourceless" {@code JobRepository}) and makes Boot's
 * batch auto-configuration back off, so this class is the single source of truth:
 *
 * <ul>
 *   <li><strong>Metadata lives in the {@code public} schema.</strong> The app's single DataSource is
 *       routed per tenant by rewriting {@code search_path}, so unqualified {@code BATCH_*} SQL could
 *       hit an arbitrary tenant schema. The {@code public.BATCH_} table prefix schema-qualifies every
 *       {@code JobRepository} query (tables and sequences alike), and the matching DDL is owned by
 *       the common Flyway migration {@code V20260709100001__spring_batch_metadata.sql}. Batch
 *       metadata is operational — global like {@code public.organizations} — while the domain-facing
 *       job state stays in the per-tenant {@code integration_sync_jobs} projection.</li>
 *   <li><strong>Asynchronous launches.</strong> The {@code JobOperator} built by this configuration is
 *       a {@code TaskExecutorJobOperator} running on the shared {@code taskExecutor} (virtual
 *       threads), so {@code start(job, parameters)} registers the execution and returns immediately —
 *       that is what lets the REST endpoints answer {@code 202 Accepted} while the job runs.</li>
 * </ul>
 *
 * <p>{@code spring.batch.job.enabled=false} keeps Boot from replaying registered jobs at startup;
 * jobs run only when the API launches them with explicit parameters.
 */
@Configuration
public class BatchConfiguration extends JdbcDefaultBatchConfiguration {

    private final TaskExecutor taskExecutor;

    public BatchConfiguration(@Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Override
    protected String getTablePrefix() {
        return "public.BATCH_";
    }

    @Override
    protected TaskExecutor getTaskExecutor() {
        return taskExecutor;
    }
}
