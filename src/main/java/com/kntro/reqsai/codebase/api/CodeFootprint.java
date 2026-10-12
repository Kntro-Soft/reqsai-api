package com.kntro.reqsai.codebase.api;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * How much of the client's code is connected to a set of projects.
 *
 * @param repositories
 *            connected repositories, whatever their state
 * @param ready
 *            repositories whose module map is ready for the copilot
 * @param failed
 *            repositories whose last indexing failed
 * @param modules
 *            indexed modules across them
 * @param files
 *            source files read while indexing
 * @param lastIndexedAt
 *            when a repository was last indexed, null when none has been
 */
public record CodeFootprint(long repositories, long ready, long failed, long modules, long files,
        @Nullable Instant lastIndexedAt) {

    /** No code connected. */
    public static CodeFootprint none() {
        return new CodeFootprint(0, 0, 0, 0, 0, null);
    }
}
