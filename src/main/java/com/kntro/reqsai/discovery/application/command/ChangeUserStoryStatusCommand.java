package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.StoryStatus;

import java.util.UUID;

/**
 * Intent to record the review decision on a user story: approve it, reject it, or send it back to
 * draft. Scoped to a project: the story must belong to {@code projectId} or the change is rejected with
 * a 404.
 *
 * @param projectId project the story must belong to
 * @param storyId   story to review
 * @param status    new review status ({@code DRAFT}, {@code APPROVED} or {@code REJECTED})
 */
public record ChangeUserStoryStatusCommand(
        UUID projectId,
        UUID storyId,
        StoryStatus status
) {
}
