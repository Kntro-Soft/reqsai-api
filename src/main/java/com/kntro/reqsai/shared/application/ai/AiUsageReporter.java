package com.kntro.reqsai.shared.application.ai;

/**
 * Receives the usage of every billable AI call, for the organization bound to the calling thread.
 * <p>
 * Best-effort by contract: implementations never throw and never make the caller wait for a database,
 * because reporting must not slow down or fail the AI call that was just paid for. Any
 * {@link AiUsageScope} of the calling thread fills in the feature and project the caller left open.
 */
@FunctionalInterface
public interface AiUsageReporter {

    /** A reporter that drops everything, for code that runs without metering (unit tests). */
    AiUsageReporter NONE = usage -> { };

    void report(TokenUsage usage);
}
