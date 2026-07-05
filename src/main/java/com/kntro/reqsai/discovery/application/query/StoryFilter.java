package com.kntro.reqsai.discovery.application.query;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Optional server-side filters for the project backlog listing. Every field is nullable — a
 * {@code null} means "no restriction", so the empty filter ({@link #none()}) preserves the original
 * unfiltered listing behavior. Applied in the repository (never in memory) so paging stays correct.
 *
 * @param search        case-insensitive substring matched across {@code title}, {@code role} and {@code action}; {@code null}/blank = no text filter
 * @param status        exact {@link StoryStatus} match; {@code null} = any status
 * @param priority      exact {@link Priority} match; {@code null} = any priority
 * @param createdAfter  lower bound on {@code createdAt}, <strong>inclusive</strong> ({@code createdAt >= createdAfter}); {@code null} = unbounded
 * @param createdBefore upper bound on {@code createdAt}, <strong>exclusive</strong> ({@code createdAt < createdBefore}); {@code null} = unbounded
 */
public record StoryFilter(
        @Nullable String search,
        @Nullable StoryStatus status,
        @Nullable Priority priority,
        @Nullable Instant createdAfter,
        @Nullable Instant createdBefore) {

    private static final StoryFilter NONE = new StoryFilter(null, null, null, null, null);

    /** The empty filter — no restriction on any field (original listing behavior). */
    public static StoryFilter none() {
        return NONE;
    }

    /**
     * Normalizes the raw {@code search} term: a {@code null}/blank string becomes {@code null} so a
     * bare {@code ?search=} is treated as "no filter" rather than matching everything with an empty
     * {@code LIKE}.
     */
    public @Nullable String normalizedSearch() {
        return (search == null || search.isBlank()) ? null : search.strip();
    }
}
