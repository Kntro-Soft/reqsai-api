package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * {@link StreamScheduler} over a small {@link ScheduledThreadPoolExecutor} of daemon threads, so it never
 * keeps the JVM alive. Task failures are logged and swallowed: an uncaught exception would otherwise
 * silently stop a periodic keepalive.
 */
@Slf4j
final class ExecutorStreamScheduler implements StreamScheduler {

    static final ExecutorStreamScheduler SHARED = new ExecutorStreamScheduler(2);

    private final ScheduledThreadPoolExecutor executor;

    ExecutorStreamScheduler(int threads) {
        this.executor = new ScheduledThreadPoolExecutor(threads,
                Thread.ofPlatform().daemon().name("stt-stream-", 1).factory());
        this.executor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public Cancellable schedule(Runnable task, Duration delay) {
        ScheduledFuture<?> future = executor.schedule(guarded(task), delay.toNanos(), TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public Cancellable scheduleAtFixedRate(Runnable task, Duration period) {
        long nanos = period.toNanos();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(guarded(task), nanos, nanos, TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    private static Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("Live STT stream task failed: {}", e.getMessage(), e);
            }
        };
    }
}
