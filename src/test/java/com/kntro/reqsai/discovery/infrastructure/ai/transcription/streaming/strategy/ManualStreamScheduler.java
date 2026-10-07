package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic {@link StreamScheduler}: nothing runs until the test advances the virtual clock, and then
 * every due task runs on the test thread in due-time order.
 */
final class ManualStreamScheduler implements StreamScheduler {

    private final List<Task> tasks = new ArrayList<>();
    private Duration now = Duration.ZERO;

    @Override
    public Cancellable schedule(Runnable task, Duration delay) {
        Task scheduled = new Task(task, now.plus(delay), null);
        tasks.add(scheduled);
        return () -> scheduled.cancelled = true;
    }

    @Override
    public Cancellable scheduleAtFixedRate(Runnable task, Duration period) {
        Task scheduled = new Task(task, now.plus(period), period);
        tasks.add(scheduled);
        return () -> scheduled.cancelled = true;
    }

    /** Moves the clock forward by {@code amount}, running every task that falls due on the way. */
    void advance(Duration amount) {
        Duration target = now.plus(amount);
        while (true) {
            Task next = tasks.stream()
                    .filter(t -> !t.cancelled && t.due.compareTo(target) <= 0)
                    .min(Comparator.comparing(t -> t.due))
                    .orElse(null);
            if (next == null) {
                break;
            }
            now = next.due;
            if (next.period == null) {
                tasks.remove(next);
            } else {
                next.due = next.due.plus(next.period);
            }
            next.action.run();
        }
        now = target;
        tasks.removeIf(t -> t.cancelled);
    }

    /** Number of periodic tasks still scheduled (e.g. keepalive timers). */
    long activePeriodicTasks() {
        return tasks.stream().filter(t -> !t.cancelled && t.period != null).count();
    }

    /** Number of tasks of any kind still scheduled. */
    long activeTasks() {
        return tasks.stream().filter(t -> !t.cancelled).count();
    }

    private static final class Task {
        private final Runnable action;
        private final Duration period;
        private Duration due;
        private boolean cancelled;

        Task(Runnable action, Duration due, Duration period) {
            this.action = action;
            this.due = due;
            this.period = period;
        }
    }
}
