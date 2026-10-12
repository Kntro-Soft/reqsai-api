package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.shared.application.ai.TokenUsage;

/**
 * Output port for reporting AI token consumption to whoever meters quota (Billing).
 * <p>
 * Implementations must be best-effort and non-throwing: metering must never break the AI generation
 * hot path. The generation adapters call {@link #record(TokenUsage)} after each model invocation with
 * what the provider reported (input, output, cached input, model, call id). The older
 * {@link #record(long)} is kept for callers that only know a total.
 */
@FunctionalInterface
public interface TokenUsageRecorderPort {

    /**
     * Reports token consumption for the current request's organization.
     *
     * @param totalTokens total tokens (prompt + completion) reported by the model, or 0 when unknown
     */
    void record(long totalTokens);

    /**
     * Reports one model call with its input, output and cached-input tokens, model and feature. The default
     * keeps older implementations working by reporting the number the call adds to the quota.
     */
    default void record(TokenUsage usage) {
        record(usage.quotaTokens());
    }
}
