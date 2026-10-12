package com.kntro.reqsai.codebase.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Codebase: short-lived archive cache")
class ArchiveCacheTest {

    private final List<Path> created = new ArrayList<>();
    private ArchiveCache cache;

    @AfterEach
    void cleanUp() throws IOException {
        if (cache != null) cache.shutdown();
        for (Path p : created) Files.deleteIfExists(p);
    }

    private Path file(int bytes) {
        try {
            Path p = Files.createTempFile("archive-cache-test-", ".zip");
            Files.write(p, new byte[bytes]);
            created.add(p);
            return p;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ArchiveCache cache(Duration ttl, long maxBytes, int maxEntries) {
        cache = new ArchiveCache(Runnable::run, ttl, maxBytes, maxEntries);
        return cache;
    }

    @Test
    @DisplayName("a second request for the same commit reuses the downloaded file")
    void reuses() throws Exception {
        cache(Duration.ofMinutes(10), 1_000, 4);
        AtomicInteger downloads = new AtomicInteger();

        try (var first = cache.acquire("org|a/b@sha", () -> {
            downloads.incrementAndGet();
            return file(8);
        }, Duration.ofSeconds(2));
             var second = cache.acquire("org|a/b@sha", () -> {
                 downloads.incrementAndGet();
                 return file(8);
             }, Duration.ofSeconds(2))) {
            assertThat(second.path()).isEqualTo(first.path());
        }
        assertThat(downloads).hasValue(1);
    }

    @Test
    @DisplayName("an expired archive is deleted once nobody reads it, never while it is read")
    void expiryWaitsForReaders() throws Exception {
        cache(Duration.ofMillis(30), 1_000, 4);
        var lease = cache.acquire("k", () -> file(8), Duration.ofSeconds(2));
        Path path = lease.path();

        Thread.sleep(80);
        cache.evict();
        assertThat(path).exists();
        assertThat(cache.size()).isZero();

        lease.close();
        assertThat(path).doesNotExist();
    }

    @Test
    @DisplayName("over the byte budget the oldest archive goes first")
    void byteBudget() throws Exception {
        cache(Duration.ofMinutes(10), 10, 4);
        Path first;
        try (var a = cache.acquire("a", () -> file(8), Duration.ofSeconds(2))) {
            first = a.path();
        }
        Thread.sleep(5);
        Path second;
        try (var b = cache.acquire("b", () -> file(8), Duration.ofSeconds(2))) {
            second = b.path();
        }

        assertThat(first).doesNotExist();
        assertThat(second).exists();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("a failed download is not cached")
    void failureIsNotCached() throws Exception {
        cache(Duration.ofMinutes(10), 1_000, 4);

        assertThatThrownBy(() -> cache.acquire("k", () -> {
            throw new IllegalStateException("boom");
        }, Duration.ofSeconds(2))).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        try (var lease = cache.acquire("k", () -> file(8), Duration.ofSeconds(2))) {
            assertThat(lease.path()).exists();
        }
    }

    @Test
    @DisplayName("a caller that runs out of time stops waiting, and the download still lands in the cache")
    void timeoutKeepsDownloading() throws Exception {
        var pool = Executors.newVirtualThreadPerTaskExecutor();
        cache = new ArchiveCache(pool::execute, Duration.ofMinutes(10), 1_000, 4);
        CountDownLatch release = new CountDownLatch(1);

        assertThatThrownBy(() -> cache.acquire("k", () -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return file(8);
        }, Duration.ofMillis(50))).isInstanceOf(TimeoutException.class);

        release.countDown();
        try (var lease = cache.acquire("k", () -> file(99), Duration.ofSeconds(3))) {
            assertThat(Files.size(lease.path())).isEqualTo(8);
        }
        pool.close();
    }

    @Test
    @DisplayName("stopping the server deletes every cached file")
    void shutdownDeletes() throws Exception {
        cache(Duration.ofMinutes(10), 1_000, 4);
        Path path;
        try (var lease = cache.acquire("k", () -> file(8), Duration.ofSeconds(2))) {
            path = lease.path();
        }

        cache.shutdown();

        assertThat(path).doesNotExist();
    }
}
