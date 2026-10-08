package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.StoryFeedbackKind;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A client approves or comments on a story through a share link.
 *
 * @param token      the raw token of the share link
 * @param storyId    the story the feedback is about
 * @param kind       approval or comment
 * @param authorName the name the client signs with
 * @param comment    the comment; optional for an approval
 */
public record LeaveStoryFeedbackCommand(
        String token, UUID storyId, StoryFeedbackKind kind, String authorName, @Nullable String comment) {
}
