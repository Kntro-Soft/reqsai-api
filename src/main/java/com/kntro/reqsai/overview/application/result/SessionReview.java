package com.kntro.reqsai.overview.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * One meeting whose review is finished.
 *
 * @param sessionId
 *            the session
 * @param title
 *            its title
 * @param purpose
 *            {@code DISCOVERY} or {@code CLARIFICATION}
 * @param endedAt
 *            when the meeting ended
 * @param seconds
 *            from the end of the meeting to the last decision on its stories, never negative
 */
public record SessionReview(UUID sessionId, String title, String purpose, Instant endedAt, long seconds) {
}
