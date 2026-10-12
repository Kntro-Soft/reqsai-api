package com.kntro.reqsai.shared.application.ai;

import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Names the feature and project an AI call belongs to, for the code below the entry point that knows
 * them. The class that calls a model (a generation adapter, an embedding adapter) does not know whether
 * it serves the chat or a live meeting; the handler that started the work does, so it opens a scope and
 * the adapter reads it when it reports usage.
 *
 * <p>The scope is bound to the calling thread and restored when the block ends, so nesting works and
 * nothing leaks into a pooled thread.
 */
public final class AiUsageScope {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private AiUsageScope() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Feature and project of the work in progress. */
    public record Scope(AiFeature feature, @Nullable UUID projectId) {}

    /** The scope of the calling thread, or {@code null} outside any. */
    public static @Nullable Scope current() {
        return CURRENT.get();
    }

    /** The feature of the calling thread's scope, or {@code fallback} outside any. */
    public static AiFeature featureOr(AiFeature fallback) {
        Scope scope = CURRENT.get();
        return scope != null ? scope.feature() : fallback;
    }

    public static <T> T call(AiFeature feature, @Nullable UUID projectId, Supplier<T> action) {
        Scope previous = CURRENT.get();
        CURRENT.set(new Scope(feature, projectId));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void run(AiFeature feature, @Nullable UUID projectId, Runnable action) {
        call(feature, projectId, () -> {
            action.run();
            return null;
        });
    }
}
