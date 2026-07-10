package com.kntro.reqsai.gateway.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable background sync job (ADR-0023): one row per async Jira {@code IMPORT} / {@code PUSH_ALL}
 * run. The row is the <strong>source of truth</strong> for progress — the async worker updates the
 * counters per item and mirrors every update to STOMP, so a page reload recovers the live state by
 * querying the job endpoints.
 *
 * <p>Counting rules: {@code processed} counts every handled item; {@code succeeded} the created/pushed
 * ones; {@code failed} the per-item failures (which never abort the run). An import duplicate counts
 * toward {@code processed} only (skipped — neither succeeded nor failed). Terminal transitions set
 * {@code finishedAt} and an optional bounded {@code message} (fatal-error summary or skip note).
 */
@Entity
@Table(name = "integration_sync_jobs")
@Getter
public class IntegrationSyncJob extends AggregateRoot {

    private static final int MESSAGE_MAX = 1000;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 16, updatable = false)
    private IntegrationSyncJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IntegrationSyncJobStatus status;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "processed", nullable = false)
    private int processed;

    @Column(name = "succeeded", nullable = false)
    private int succeeded;

    @Column(name = "failed", nullable = false)
    private int failed;

    @Column(name = "message", length = MESSAGE_MAX)
    @Nullable
    private String message;

    @Column(name = "requested_by", columnDefinition = "uuid", updatable = false)
    @Nullable
    private UUID requestedBy;

    @Column(name = "finished_at")
    @Nullable
    private Instant finishedAt;

    protected IntegrationSyncJob() {
        super();
    }

    /** Starts a new job in {@code RUNNING} state. {@code total} may be 0 until the worker resolves it. */
    public IntegrationSyncJob(UUID projectId, IntegrationSyncJobType jobType, int total, @Nullable UUID requestedBy) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.jobType = Assert.notNull(jobType, "jobType");
        this.status = IntegrationSyncJobStatus.RUNNING;
        this.total = Math.max(0, total);
        this.requestedBy = requestedBy;
    }

    public boolean isRunning() {
        return status == IntegrationSyncJobStatus.RUNNING;
    }

    /** Fixes the item count once the worker knows how many items it will process. */
    public void planTotal(int total) {
        assertRunning();
        this.total = Math.max(0, total);
    }

    /** One item created/pushed successfully. */
    public void recordSuccess() {
        assertRunning();
        processed++;
        succeeded++;
    }

    /** One item failed (the run continues). */
    public void recordFailure() {
        assertRunning();
        processed++;
        failed++;
    }

    /** One item skipped (e.g. an import duplicate): processed, but neither succeeded nor failed. */
    public void recordSkipped() {
        assertRunning();
        processed++;
    }

    /** Terminal success (per-item failures allowed); {@code message} is an optional summary note. */
    public void complete(@Nullable String message) {
        assertRunning();
        this.status = IntegrationSyncJobStatus.COMPLETED;
        this.message = truncate(message);
        this.finishedAt = Instant.now();
    }

    /** Terminal fatal failure (e.g. the tracker was unreachable before/while iterating). */
    public void fail(@Nullable String message) {
        assertRunning();
        this.status = IntegrationSyncJobStatus.FAILED;
        this.message = truncate(message);
        this.finishedAt = Instant.now();
    }

    private void assertRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("Job " + getId() + " is terminal (" + status + ")");
        }
    }

    @Nullable
    private static String truncate(@Nullable String message) {
        if (message == null || message.length() <= MESSAGE_MAX) {
            return message;
        }
        return message.substring(0, MESSAGE_MAX);
    }
}
