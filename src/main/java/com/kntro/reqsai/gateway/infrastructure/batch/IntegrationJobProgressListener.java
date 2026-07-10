package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.gateway.application.notification.IntegrationJobProgressNotifier;
import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.ItemProcessListener;
import org.springframework.batch.core.listener.SkipListener;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Per-item progress bridge from the batch step to the domain projection: after each processed item
 * it updates the {@code integration_sync_jobs} counters and publishes the fresh snapshot to the
 * project's STOMP topic. Registered twice on the step:
 *
 * <ul>
 *   <li>{@link ItemProcessListener#afterProcess} — normal path; the processor reports the outcome
 *       ({@code SUCCEEDED}/{@code SKIPPED}/{@code FAILED}) without throwing.</li>
 *   <li>{@link SkipListener#onSkipInProcess} — fault-tolerant path; the processor threw, the step's
 *       skip policy swallowed the exception, and the item counts as a failure.</li>
 * </ul>
 *
 * <p>Instantiated {@code @StepScope} (one instance per step execution) because the target row id
 * comes from the {@code domainJobId} job parameter. Counter writes join the surrounding chunk
 * transaction — durable at each chunk boundary — while STOMP frames go out immediately; if a chunk
 * rolls back for item-by-item skip rescanning, the counters are re-derived from the reverted row, so
 * the projection stays consistent (a transient duplicate STOMP frame is harmless for a progress
 * banner).
 */
@RequiredArgsConstructor
@Slf4j
public class IntegrationJobProgressListener
        implements ItemProcessListener<Object, SyncItemOutcome>, SkipListener<Object, Object> {

    private final UUID domainJobId;
    private final IntegrationSyncJobRepository jobs;
    private final IntegrationJobProgressNotifier progress;

    @Override
    public void afterProcess(Object item, SyncItemOutcome outcome) {
        if (outcome == null) {
            return; // item filtered out by the processor; nothing to count
        }
        record(job -> {
            switch (outcome) {
                case SUCCEEDED -> job.recordSuccess();
                case SKIPPED -> job.recordSkipped();
                case FAILED -> job.recordFailure();
            }
        });
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        log.warn("Integration job {} skipped one item: {}", domainJobId, t.getMessage());
        record(IntegrationSyncJob::recordFailure);
    }

    private void record(Consumer<IntegrationSyncJob> update) {
        jobs.findById(domainJobId).filter(IntegrationSyncJob::isRunning).ifPresent(job -> {
            update.accept(job);
            progress.publish(jobs.save(job));
        });
    }
}
