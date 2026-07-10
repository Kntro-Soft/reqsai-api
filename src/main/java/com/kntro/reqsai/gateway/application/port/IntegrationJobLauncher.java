package com.kntro.reqsai.gateway.application.port;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Port for launching the asynchronous execution of an integration sync job. The handlers create the
 * durable {@code integration_sync_jobs} row first (the API's source of truth) and then hand the run
 * to this port; the engine behind it is an infrastructure detail (currently Spring Batch — see
 * {@code gateway.infrastructure.batch}). Implementations must return immediately (the endpoints
 * answer {@code 202 Accepted}) and must propagate the caller's tenant to the execution.
 */
public interface IntegrationJobLauncher {

    /** Starts the Jira import run for an already-persisted RUNNING job row. */
    void launchImport(UUID jobId, UUID projectId, @Nullable List<String> issueKeys);

    /** Starts the push-all run for an already-persisted RUNNING job row. */
    void launchPushAll(UUID jobId, UUID projectId);
}
