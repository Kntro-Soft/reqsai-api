package com.kntro.reqsai.discovery.insights;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A meeting that ended in a period, and when the review of the stories it produced finished.
 *
 * @param sessionId
 *            the session
 * @param title
 *            its title
 * @param purpose
 *            {@code DISCOVERY} or {@code CLARIFICATION}
 * @param endedAt
 *            when the recording ended or the transcript was uploaded
 * @param reviewFinishedAt
 *            the instant the last of its stories was approved or rejected, once none is left in draft; null while
 *            the review is pending, when the session produced no story, or when a decision time was not recorded
 */
public record HeldSession(UUID sessionId, String title, String purpose, Instant endedAt,
        @Nullable Instant reviewFinishedAt) {
}
