package com.kntro.reqsai.overview.application.result;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** One of the busiest projects of the organization. */
public record ProjectActivitySummary(UUID projectId, String name, boolean demo, long stories, long sessions,
        @Nullable Instant lastActivityAt) {
}
