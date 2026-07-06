package com.kntro.reqsai.discovery.application.query;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Cursor-paginated query for a discovery session's finalized transcript segments, newest-first, so the
 * frontend can rebuild the chat timeline of a past (potentially hours-long) session in chunks without
 * pulling thousands of segments at once.
 *
 * @param sessionId      the session whose segments to page
 * @param beforeSequence exclusive upper bound — return finals with {@code sequence < beforeSequence};
 *                       {@code null} means "from the newest segment"
 * @param limit          max segments to return; {@code null} falls back to the handler default, and any
 *                       value is clamped to the handler cap
 */
public record ListSessionSegmentsQuery(UUID sessionId, @Nullable Integer beforeSequence, @Nullable Integer limit) {
}
