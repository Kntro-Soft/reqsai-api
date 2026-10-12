package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.StoryStatus;

import java.util.UUID;

/** A reviewer with a session link approves, rejects or sends back to draft a story of the session. */
public record ChangeSharedStoryStatusCommand(String token, UUID storyId, StoryStatus status) {
}
