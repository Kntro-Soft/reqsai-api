package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of a background integration sync job. Returned by the 202 job-start endpoints and the
 * job query endpoints, and broadcast with the <strong>same JSON shape</strong> on
 * {@code /topic/projects/{projectId}/integration-jobs} — live frames and reload-recovery reads are
 * interchangeable for the client.
 */
@Schema(description = "Background integration sync job (import / push-all) with live progress counters")
public record IntegrationJobResponse(
        UUID id,
        UUID projectId,
        @Schema(description = "IMPORT | PUSH_ALL") String jobType,
        @Schema(description = "RUNNING | COMPLETED | FAILED") String status,
        int total,
        int processed,
        int succeeded,
        int failed,
        @Schema(description = "Terminal summary (fatal error or skipped-duplicates note); null otherwise")
        @Nullable String message,
        Instant createdAt,
        @Nullable Instant finishedAt) {}
