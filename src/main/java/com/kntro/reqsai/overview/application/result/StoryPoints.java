package com.kntro.reqsai.overview.application.result;

/**
 * Estimation of the live backlog.
 *
 * @param total
 *            points of every live story
 * @param approved
 *            points of the approved or exported stories
 * @param unestimated
 *            live stories with no points yet
 */
public record StoryPoints(long total, long approved, long unestimated) {
}
