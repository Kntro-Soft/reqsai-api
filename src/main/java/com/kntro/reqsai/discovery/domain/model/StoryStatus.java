package com.kntro.reqsai.discovery.domain.model;

/**
 * Review lifecycle of a {@link UserStory}
 * <pre>
 *   DRAFT ──approve──▶ APPROVED ──export──▶ EXPORTED
 *   DRAFT ──reject───▶ REJECTED
 *   DRAFT ──merge────▶ MERGED (folded into another story)
 * </pre>
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
    EXPORTED
}
