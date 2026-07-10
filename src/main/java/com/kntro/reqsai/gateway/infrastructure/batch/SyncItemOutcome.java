package com.kntro.reqsai.gateway.infrastructure.batch;

/**
 * Per-item result flowing from the batch item processors to the progress listener. {@code SKIPPED}
 * is an import duplicate (processed but neither succeeded nor failed); {@code FAILED} is a per-item
 * failure the processor captured itself (the run continues). Items whose processor <em>throws</em>
 * instead are routed through the step's skip policy and land in the SkipListener, not here.
 */
public enum SyncItemOutcome {
    SUCCEEDED,
    SKIPPED,
    FAILED
}
