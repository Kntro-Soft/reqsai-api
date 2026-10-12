package com.kntro.reqsai.codebase.application.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Keeps the repository archives of recent deep searches in temporary files, so the follow-up questions of one
 * conversation do not download the repository again. An archive lives for a few minutes after its last use
 * ({@code reqsai.codebase.deep-search.cache-ttl}), the cache holds at most a few files and a byte budget
 * (the oldest are dropped first), a file in use is deleted as soon as its last reader is done, and every file is
 * deleted when the server stops. Nothing here is persisted: it is plain temporary files.
 *
 * <p>A download runs on its own thread: a caller that runs out of time stops waiting, and the download still
 * finishes into the cache for the next request. Keys are scoped by the caller (organization + repository +
 * commit), so one organization never reads another's archive.
 */
@Component
@Slf4j
public class ArchiveCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final TaskExecutor executor;
    private final Duration ttl;
    private final long maxBytes;
    private final int maxEntries;
    private final ScheduledExecutorService janitor;

    public ArchiveCache(@Qualifier("taskExecutor") TaskExecutor executor,
                        @Value("${reqsai.codebase.deep-search.cache-ttl:PT10M}") Duration ttl,
                        @Value("${reqsai.codebase.deep-search.cache-max-bytes:419430400}") long maxBytes,
                        @Value("${reqsai.codebase.deep-search.cache-max-entries:4}") int maxEntries) {
        this.executor = executor;
        this.ttl = ttl;
        this.maxBytes = maxBytes;
        this.maxEntries = Math.max(1, maxEntries);
        this.janitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reqsai-archive-cache");
            thread.setDaemon(true);
            return thread;
        });
        long period = Math.max(5, Math.min(60, ttl.toSeconds() / 2 + 1));
        this.janitor.scheduleWithFixedDelay(this::evict, period, period, TimeUnit.SECONDS);
    }

    /** A reader's hold on a cached archive; closing it lets the cache delete the file once it expired. */
    public final class Lease implements AutoCloseable {

        private final Entry entry;
        private boolean closed;

        private Lease(Entry entry) {
            this.entry = entry;
        }

        public Path path() {
            return entry.path();
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            entry.release();
        }
    }

    /**
     * The archive for {@code key}, downloaded by {@code loader} when it is not cached, waiting at most
     * {@code timeout}. Throws {@link TimeoutException} when the download is still running by then (it keeps
     * going into the cache); a failed download is not cached.
     */
    public Lease acquire(String key, Supplier<Path> loader, Duration timeout) throws TimeoutException {
        evict();
        for (int attempt = 0; attempt < 3; attempt++) {
            Entry entry = entries.get(key);
            if (entry == null) {
                Entry fresh = new Entry(key);
                Entry previous = entries.putIfAbsent(key, fresh);
                entry = previous == null ? fresh : previous;
                if (previous == null) start(fresh, loader);
            }
            if (!entry.retain()) {
                entries.remove(key, entry);
                continue;
            }
            try {
                entry.future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
                entry.touch(ttl);
                return new Lease(entry);
            } catch (TimeoutException e) {
                entry.release();
                throw e;
            } catch (ExecutionException e) {
                entry.release();
                entries.remove(key, entry);
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Could not download the repository archive", cause);
            } catch (InterruptedException e) {
                entry.release();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while downloading the repository archive", e);
            }
        }
        throw new IllegalStateException("The repository archive could not be cached");
    }

    private void start(Entry entry, Supplier<Path> loader) {
        executor.execute(() -> {
            try {
                Path path = loader.get();
                entry.complete(path, Files.size(path), ttl);
                enforceBudget();
            } catch (Throwable t) {
                entry.future.completeExceptionally(t);
            }
        });
    }

    /** Drops expired archives and, over the byte or entry budget, the least recently used ones. */
    void evict() {
        Instant now = Instant.now();
        for (Entry e : List.copyOf(entries.values())) {
            if (e.isDone() && e.expiresAt().isBefore(now)) drop(e);
        }
        enforceBudget();
    }

    private void enforceBudget() {
        List<Entry> ready = new ArrayList<>(entries.values().stream().filter(Entry::isDone).toList());
        ready.sort(Comparator.comparing(Entry::expiresAt));
        long total = ready.stream().mapToLong(e -> e.size).sum();
        while (!ready.isEmpty() && (ready.size() > maxEntries || total > maxBytes)) {
            Entry oldest = ready.removeFirst();
            total -= oldest.size;
            drop(oldest);
        }
    }

    private void drop(Entry entry) {
        if (entries.remove(entry.key, entry)) {
            entry.expire();
            log.debug("Archive cache dropped {}", entry.key);
        }
    }

    /** Number of archives currently held. */
    public int size() {
        return entries.size();
    }

    @PreDestroy
    void shutdown() {
        janitor.shutdownNow();
        for (Entry e : List.copyOf(entries.values())) drop(e);
    }

    private static final class Entry {

        private final String key;
        private final CompletableFuture<Path> future = new CompletableFuture<>();
        private final AtomicInteger users = new AtomicInteger();
        private volatile Instant expiresAt = Instant.MAX;
        private volatile boolean expired;
        private volatile long size;

        private Entry(String key) {
            this.key = key;
        }

        Path path() {
            return future.join();
        }

        boolean isDone() {
            return future.isDone() && !future.isCompletedExceptionally();
        }

        Instant expiresAt() {
            return expiresAt;
        }

        void complete(Path path, long bytes, Duration ttl) {
            this.size = bytes;
            this.expiresAt = Instant.now().plus(ttl);
            future.complete(path);
            if (expired) deleteFile();
        }

        void touch(Duration ttl) {
            this.expiresAt = Instant.now().plus(ttl);
        }

        synchronized boolean retain() {
            if (expired) return false;
            users.incrementAndGet();
            return true;
        }

        void release() {
            if (users.decrementAndGet() <= 0 && expired) deleteFile();
        }

        void expire() {
            expired = true;
            if (users.get() <= 0 && future.isDone()) deleteFile();
        }

        private synchronized void deleteFile() {
            if (!future.isDone() || future.isCompletedExceptionally()) return;
            try {
                Files.deleteIfExists(future.join());
            } catch (IOException e) {
                log.debug("Could not delete cached archive for {}", key);
            }
        }
    }
}
