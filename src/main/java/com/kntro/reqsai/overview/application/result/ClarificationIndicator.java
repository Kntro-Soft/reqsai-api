package com.kntro.reqsai.overview.application.result;

import org.jspecify.annotations.Nullable;

/**
 * The follow-up meetings held to clarify requirements.
 *
 * @param meetings
 *            meetings of the period created as clarification meetings
 * @param sessions
 *            every meeting of the period
 */
public record ClarificationIndicator(long meetings, long sessions) {

    /** Share of the period's meetings that were clarification meetings; null when there was no meeting. */
    public @Nullable Double share() {
        return sessions == 0 ? null : (double) meetings / sessions;
    }
}
