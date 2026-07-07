package com.kntro.reqsai.discovery.application.port;

/**
 * Output port for reporting AI token consumption to whoever meters quota (Billing).
 * <p>
 * Implementations must be best-effort and non-throwing: metering must never break the AI generation
 * hot path. The generation adapters call this after each model invocation with the provider-reported
 * total token count.
 */
@FunctionalInterface
public interface TokenUsageRecorderPort {

    /**
     * Reports token consumption for the current request's organization.
     *
     * @param totalTokens total tokens (prompt + completion) reported by the model, or 0 when unknown
     */
    void record(long totalTokens);
}
