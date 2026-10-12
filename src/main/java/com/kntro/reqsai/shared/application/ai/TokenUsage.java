package com.kntro.reqsai.shared.application.ai;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One billable AI call as the provider reported it. Immutable; built where the provider response is read
 * and handed to an {@link AiUsageReporter}.
 *
 * <p><strong>Quota weight.</strong> {@link #quotaTokens()} is the number this call adds to the
 * organization's monthly counter, and it is exactly what the ledger stores in {@code quota_tokens}:
 * <pre>
 *   uncached input x 1  +  cached input x {@value #CACHED_INPUT_WEIGHT_PERCENT} %  +  output x 1
 * </pre>
 * A prompt prefix the provider served from its cache costs the provider half as much (OpenAI bills cached
 * input at 50 %), so it counts half against the plan: structuring a prompt for caching lowers the quota
 * it uses. Embeddings count at {@value #EMBEDDING_WEIGHT_PERCENT} % because they cost about a tenth of
 * generation per token, and speech-to-text is metered in seconds and never counts as tokens.
 *
 * @param feature            the feature the call was made for
 * @param projectId          the project it belongs to, or {@code null} when none applies
 * @param model              the model the provider reports having served, or {@code null} when unknown
 * @param inputTokens        prompt tokens (cached ones included)
 * @param outputTokens       completion tokens, including any reasoning tokens
 * @param cachedInputTokens  the part of {@code inputTokens} the provider served from its prompt cache
 * @param seconds            audio seconds, for {@link AiFeature#TRANSCRIPTION}
 * @param idempotencyKey     identifies the provider call; the same key is only ever recorded once
 */
public record TokenUsage(
        AiFeature feature,
        @Nullable UUID projectId,
        @Nullable String model,
        long inputTokens,
        long outputTokens,
        long cachedInputTokens,
        long seconds,
        String idempotencyKey
) {

    /** Share of a cached input token that counts against the quota. */
    public static final int CACHED_INPUT_WEIGHT_PERCENT = 50;

    /** Share of an embedding token that counts against the quota. */
    public static final int EMBEDDING_WEIGHT_PERCENT = 10;

    public TokenUsage {
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        cachedInputTokens = Math.min(Math.max(0, cachedInputTokens), inputTokens);
        seconds = Math.max(0, seconds);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }
    }

    /** A generation call. */
    public static TokenUsage of(AiFeature feature, @Nullable UUID projectId, @Nullable String model,
                                long input, long output, long cachedInput, @Nullable String idempotencyKey) {
        return new TokenUsage(feature, projectId, model, input, output, cachedInput, 0, idempotencyKey);
    }

    /** Speech-to-text time. */
    public static TokenUsage audio(@Nullable UUID projectId, @Nullable String model, long seconds) {
        return new TokenUsage(AiFeature.TRANSCRIPTION, projectId, model, 0, 0, 0, seconds, null);
    }

    /** All the tokens the provider counted for the call, as it reports them. */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    /** What this call adds to the monthly token counter; see the class comment for the rule. */
    public long quotaTokens() {
        if (feature == AiFeature.TRANSCRIPTION) {
            return 0;
        }
        long uncached = inputTokens - cachedInputTokens;
        long weighted = uncached + ceilPercent(cachedInputTokens, CACHED_INPUT_WEIGHT_PERCENT) + outputTokens;
        return feature == AiFeature.EMBEDDINGS ? ceilPercent(weighted, EMBEDDING_WEIGHT_PERCENT) : weighted;
    }

    /** A copy with the feature and project of the surrounding {@link AiUsageScope}, when it has one. */
    public TokenUsage within(AiUsageScope.@Nullable Scope scope) {
        if (scope == null) {
            return this;
        }
        UUID project = projectId != null ? projectId : scope.projectId();
        AiFeature resolved = feature == AiFeature.OTHER ? scope.feature() : feature;
        return new TokenUsage(resolved, project, model, inputTokens, outputTokens, cachedInputTokens, seconds,
                idempotencyKey);
    }

    private static long ceilPercent(long value, int percent) {
        return (value * percent + 99) / 100;
    }
}
