package com.kntro.reqsai.overview.application.result;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Time from the end of a meeting to the end of the review of the stories it produced.
 *
 * @param measured
 *            meetings of the period whose review is finished
 * @param unmeasured
 *            meetings of the period with no finished review: stories still in draft, no story, or no recorded
 *            decision time
 * @param averageSeconds
 *            mean over the measured meetings; null when there are none
 * @param medianSeconds
 *            median over the measured meetings; null when there are none
 * @param sessions
 *            the measured meetings, newest first
 */
public record PostProcessingIndicator(long measured, long unmeasured, @Nullable Long averageSeconds,
        @Nullable Long medianSeconds, List<SessionReview> sessions) {

    public PostProcessingIndicator {
        sessions = List.copyOf(sessions);
    }
}
