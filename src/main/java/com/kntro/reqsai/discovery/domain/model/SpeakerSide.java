package com.kntro.reqsai.discovery.domain.model;

/**
 * Which side of the meeting a diarized speaker is on. The AI treats what the {@link #CLIENT} says as the
 * requirements and what the {@link #TEAM} says as context.
 */
public enum SpeakerSide {

    /** The customer, user or stakeholder whose needs the product must meet. */
    CLIENT,

    /** The analysts, developers or consultants running the meeting. */
    TEAM
}
