package com.kntro.reqsai.gateway.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Domain: Integration sync job")
class IntegrationSyncJobTest {

    private static final UUID PROJECT = UUID.randomUUID();

    @Test
    @DisplayName("starts RUNNING with zeroed counters and the known total")
    void starts_running() {
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.IMPORT, 5, UUID.randomUUID());

        assertThat(job.isRunning()).isTrue();
        assertThat(job.getStatus()).isEqualTo(IntegrationSyncJobStatus.RUNNING);
        assertThat(job.getTotal()).isEqualTo(5);
        assertThat(job.getProcessed()).isZero();
        assertThat(job.getSucceeded()).isZero();
        assertThat(job.getFailed()).isZero();
        assertThat(job.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("counts items: success and failure both process; skipped only processes")
    void counts_items() {
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.IMPORT, 0, null);
        job.planTotal(3);

        job.recordSuccess();
        job.recordSkipped();
        job.recordFailure();

        assertThat(job.getTotal()).isEqualTo(3);
        assertThat(job.getProcessed()).isEqualTo(3);
        assertThat(job.getSucceeded()).isEqualTo(1);
        assertThat(job.getFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("complete() and fail() are terminal: they stamp finishedAt and freeze the job")
    void terminal_transitions() {
        IntegrationSyncJob completed = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.IMPORT, 1, null);
        completed.complete("1 duplicados omitidos");
        assertThat(completed.getStatus()).isEqualTo(IntegrationSyncJobStatus.COMPLETED);
        assertThat(completed.getMessage()).isEqualTo("1 duplicados omitidos");
        assertThat(completed.getFinishedAt()).isNotNull();
        assertThatThrownBy(completed::recordSuccess).isInstanceOf(IllegalStateException.class);

        IntegrationSyncJob failed = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.PUSH_ALL, 1, null);
        failed.fail("Jira unreachable");
        assertThat(failed.getStatus()).isEqualTo(IntegrationSyncJobStatus.FAILED);
        assertThat(failed.getMessage()).isEqualTo("Jira unreachable");
        assertThatThrownBy(() -> failed.complete(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("bounds the terminal message to the column size")
    void truncates_message() {
        IntegrationSyncJob job = new IntegrationSyncJob(PROJECT, IntegrationSyncJobType.IMPORT, 0, null);
        job.fail("x".repeat(2000));

        assertThat(job.getMessage()).hasSize(1000);
    }
}
