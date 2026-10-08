package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.domain.model.StoryFeedback;
import com.kntro.reqsai.discovery.domain.model.UserStory;

import java.time.Instant;
import java.util.List;

/**
 * What a client sees through a share link: the project's name, until when the link works, and the
 * stories under review with the feedback clients already left on each.
 */
public record SharedBacklog(String projectName, Instant expiresAt, List<SharedStory> stories) {

    /** A story as the client sees it, with every approval and comment left on it, oldest first. */
    public record SharedStory(UserStory story, List<StoryFeedback> feedback) {
    }
}
