package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Intent to permanently delete a single user story. Scoped to a project: the story must belong to
 * {@code projectId} or the delete is rejected with a 404. The story's acceptance criteria are removed
 * with it (JPA cascade / orphan removal on the aggregate).
 *
 * @param projectId project the story must belong to
 * @param storyId   story to delete
 */
public record DeleteUserStoryCommand(UUID projectId, UUID storyId) {}
