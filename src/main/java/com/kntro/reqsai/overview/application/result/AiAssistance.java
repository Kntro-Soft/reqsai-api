package com.kntro.reqsai.overview.application.result;

import org.jspecify.annotations.Nullable;

/**
 * How the analysts treated the copilot's story proposals.
 *
 * @param accepted
 *            proposals accepted into the backlog
 * @param dismissed
 *            proposals dismissed
 * @param pending
 *            proposals still awaiting a decision
 * @param questions
 *            clarifying questions the copilot raised
 */
public record AiAssistance(long accepted, long dismissed, long pending, long questions) {

    /**
     * Share of decided proposals that were accepted, between 0 and 1; null until
     * one has been decided.
     */
    public @Nullable Double acceptanceRate() {
        long decided = accepted + dismissed;
        return decided == 0 ? null : (double) accepted / decided;
    }
}
