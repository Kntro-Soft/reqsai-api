package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.Priority;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Intent to manually edit the core fields of an existing user story. Scoped to a project: the story
 * must belong to {@code projectId} or the update is rejected with a 404. A manual edit is a straight
 * field update — it does not re-run deduplication or recompute the embedding.
 *
 * @param projectId   project the story must belong to
 * @param storyId     story to update
 * @param title       short story title
 * @param role        actor ("as a …")
 * @param action      desired action ("I want to …")
 * @param benefit     expected benefit ("so that …")
 * @param priority    backlog priority
 * @param storyPoints optional effort estimate ({@code null} clears any estimate)
 */
public record UpdateUserStoryCommand(
        UUID projectId,
        UUID storyId,
        String title,
        String role,
        String action,
        String benefit,
        Priority priority,
        @Nullable Integer storyPoints
) {
}
