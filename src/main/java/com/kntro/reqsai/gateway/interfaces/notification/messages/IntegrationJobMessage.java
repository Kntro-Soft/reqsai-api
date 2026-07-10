package com.kntro.reqsai.gateway.interfaces.notification.messages;

import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJobType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload broadcast on {@code /topic/projects/{projectId}/integration-jobs} for every
 * sync-job progress update (per item) and terminal transition. The JSON shape is deliberately
 * <strong>identical</strong> to the REST {@code IntegrationJobResponse}, so the frontend renders the
 * same object whether it arrives live over STOMP or from the reload-recovery job query endpoints.
 */
public record IntegrationJobMessage(
        UUID id,
        UUID projectId,
        IntegrationSyncJobType jobType,
        IntegrationSyncJobStatus status,
        int total,
        int processed,
        int succeeded,
        int failed,
        @Nullable String message,
        Instant createdAt,
        @Nullable Instant finishedAt
) {
}
