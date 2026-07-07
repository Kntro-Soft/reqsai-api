package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.Priority;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Intent to manually create a user story under a project (starts in {@code DRAFT}).
 *
 * @param projectId   project the story belongs to (workspace context; plain id, no FK)
 * @param title       short story title
 * @param role        actor ("as a …")
 * @param action      desired action ("I want to …")
 * @param benefit     expected benefit ("so that …")
 * @param priority    backlog priority
 * @param storyPoints optional effort estimate ({@code null} if not estimated)
 */
public record CreateUserStoryCommand(
        UUID projectId,
        String title,
        String role,
        String action,
        String benefit,
        Priority priority,
        @Nullable Integer storyPoints
) {
}
