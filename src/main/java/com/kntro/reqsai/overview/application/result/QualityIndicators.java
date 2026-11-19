package com.kntro.reqsai.overview.application.result;

import java.time.LocalDate;

/**
 * The quality indicators of a project over a period, for validating the success criteria of the pilots.
 *
 * @param from
 *            first day of the period (UTC, inclusive)
 * @param to
 *            last day of the period (UTC, inclusive)
 * @param hasData
 *            false when nothing happened in the period: no meeting ended and no story was decided
 * @param sessions
 *            meetings that ended in the period
 * @param acceptance
 *            stories accepted without major rewriting
 * @param postProcessing
 *            time from the end of a meeting to the end of the review of its stories
 * @param clarification
 *            clarification meetings
 */
public record QualityIndicators(LocalDate from, LocalDate to, boolean hasData, long sessions,
        AcceptanceIndicator acceptance, PostProcessingIndicator postProcessing,
        ClarificationIndicator clarification) {
}
