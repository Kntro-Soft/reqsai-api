package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.gateway.application.notification.IntegrationJobProgressNotifier;
import com.kntro.reqsai.gateway.application.port.IntegrationSyncJobRepository;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch: per-item progress listener updates the projection and publishes each snapshot")
class IntegrationJobProgressListenerTest {

    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private IntegrationSyncJobRepository jobs;
    @Mock
    private IntegrationJobProgressNotifier progress;

    private IntegrationSyncJob job;
    private IntegrationJobProgressListener listener;

    @BeforeEach
    void setUp() {
        job = new IntegrationSyncJob(UUID.randomUUID(), IntegrationSyncJobType.IMPORT, 3, UUID.randomUUID());
        lenient().when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job));
        lenient().when(jobs.save(job)).thenReturn(job);
        listener = new IntegrationJobProgressListener(JOB_ID, jobs, progress);
    }

    @Test
    @DisplayName("afterProcess maps each outcome onto the counters and publishes per item")
    void counts_outcomes() {
        listener.afterProcess("item-1", SyncItemOutcome.SUCCEEDED);
        listener.afterProcess("item-2", SyncItemOutcome.SKIPPED);
        listener.afterProcess("item-3", SyncItemOutcome.FAILED);

        assertThat(job.getProcessed()).isEqualTo(3);
        assertThat(job.getSucceeded()).isEqualTo(1);
        assertThat(job.getFailed()).isEqualTo(1);
        verify(progress, times(3)).publish(job);
    }

    @Test
    @DisplayName("a skipped (thrown-and-swallowed) item counts as failed and publishes")
    void counts_skip_as_failure() {
        listener.onSkipInProcess("item-1", new IllegalStateException("boom"));

        assertThat(job.getProcessed()).isEqualTo(1);
        assertThat(job.getFailed()).isEqualTo(1);
        assertThat(job.getSucceeded()).isZero();
        verify(progress).publish(job);
    }

    @Test
    @DisplayName("a null outcome (filtered item) is not counted")
    void ignores_filtered_items() {
        listener.afterProcess("item-1", null);

        assertThat(job.getProcessed()).isZero();
        verify(progress, never()).publish(any());
    }

    @Test
    @DisplayName("a terminal projection row is left untouched")
    void ignores_terminal_row() {
        job.complete(null);

        listener.afterProcess("item-1", SyncItemOutcome.SUCCEEDED);

        verify(progress, never()).publish(any());
    }
}
