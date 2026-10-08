package com.kntro.reqsai.discovery.domain.model;

/**
 * Review lifecycle of a {@link UserStory}
 * <pre>
 *   DRAFT ──approve──▶ APPROVED ──export──▶ EXPORTED
 *   DRAFT ──reject───▶ REJECTED
 *   DRAFT ──merge────▶ MERGED (folded into another story)
 * </pre>
 * The review decision can be revised: {@code DRAFT}, {@code APPROVED} and {@code REJECTED} move freely
 * between each other. {@code MERGED} and {@code EXPORTED} leave review for good.
 */
public enum StoryStatus {

    /** Freshly created (manually or AI-generated), awaiting human review. */
    DRAFT,

    /** Accepted by the team into the backlog. */
    APPROVED,

    /** Discarded by the team. */
    REJECTED,

    /** Superseded / folded into another story. */
    MERGED,

    /** Pushed to an external tracker (e.g. Jira). */
    EXPORTED;

    /** True for the statuses a reviewer can set and leave: {@code DRAFT}, {@code APPROVED}, {@code REJECTED}. */
    public boolean isReviewable() {
        return this == DRAFT || this == APPROVED || this == REJECTED;
    }
}
