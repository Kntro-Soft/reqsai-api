package com.kntro.reqsai.billing.application.port;

import com.kntro.reqsai.shared.application.ai.TokenUsage;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The append-only ledger of AI calls of the organization bound to the calling thread (tenant schema).
 */
public interface AiUsageLedgerPort {

    /**
     * Appends one call. Idempotent on {@link TokenUsage#idempotencyKey()}.
     *
     * @return {@code true} when the call was stored, {@code false} when the key was already there
     */
    boolean append(TokenUsage usage, Instant at);

    /** What was used between {@code from} (inclusive) and {@code to} (exclusive). */
    Summary summarize(Instant from, Instant to, int topProjects);

    /** Tokens and calls of one feature. {@code tokens} is the quota weight, the rest is as the provider reported. */
    record FeatureTotals(String feature, long tokens, long inputTokens, long outputTokens, long cachedInputTokens,
                         long calls) {}

    /** Quota tokens of one project. */
    record ProjectTotals(UUID projectId, long tokens) {}

    /**
     * @param byFeature            one row per feature used, largest first
     * @param topProjects          the projects that used the most, largest first
     * @param transcriptionSeconds audio seconds transcribed
     */
    record Summary(List<FeatureTotals> byFeature, List<ProjectTotals> topProjects, long transcriptionSeconds) {

        public static Summary empty() {
            return new Summary(List.of(), List.of(), 0);
        }

        /** Quota tokens of one feature, 0 when it was not used. */
        public long tokensOf(@Nullable String feature) {
            return byFeature.stream().filter(f -> f.feature().equals(feature)).mapToLong(FeatureTotals::tokens).sum();
        }
    }
}
