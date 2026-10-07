package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import java.time.Duration;

/**
 * Narrow scheduling seam for the live provider streams: keepalive ticks and reconnect backoff.
 * Production uses {@link #shared()}; tests drive a manual implementation so timing is deterministic.
 */
interface StreamScheduler {

    /** Runs {@code task} once after {@code delay}. */
    Cancellable schedule(Runnable task, Duration delay);

    /** Runs {@code task} every {@code period}, the first time one period from now, until cancelled. */
    Cancellable scheduleAtFixedRate(Runnable task, Duration period);

    /** Handle to a scheduled task. Cancelling twice, or after the task ran, is a no-op. */
    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }

    /**
     * Process-wide scheduler shared by every live stream. Its tasks are short and never block (sends and
     * connects are asynchronous), so two daemon threads are enough.
     */
    static StreamScheduler shared() {
        return ExecutorStreamScheduler.SHARED;
    }
}
