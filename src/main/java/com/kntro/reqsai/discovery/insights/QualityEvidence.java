package com.kntro.reqsai.discovery.insights;

import java.util.List;

/**
 * What Discovery holds to compute the quality indicators of one project over a period.
 *
 * @param decidedStories
 *            the AI-generated stories approved, exported or rejected in the period
 * @param decidedWithoutBaseline
 *            stories decided in the period that did not come from the AI (or predate the record of its text), left
 *            out of {@code decidedStories} because there is nothing to compare them with
 * @param sessions
 *            the meetings that ended in the period, oldest first
 */
public record QualityEvidence(List<DecidedStory> decidedStories, long decidedWithoutBaseline,
        List<HeldSession> sessions) {

    public QualityEvidence {
        decidedStories = List.copyOf(decidedStories);
        sessions = List.copyOf(sessions);
    }
}
