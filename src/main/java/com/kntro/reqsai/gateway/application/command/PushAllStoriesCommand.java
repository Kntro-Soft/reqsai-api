package com.kntro.reqsai.gateway.application.command;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Push stories of a project to the project's configured Jira target (per-story failures captured).
 * {@code storyIds} optionally restricts the push to the given stories; {@code null}/empty means every
 * eligible story (the unrestricted, original behaviour). Ids not in the project are ignored.
 *
 * @param projectId   project whose stories are pushed
 * @param storyIds    the specific stories to push; {@code null}/empty means all eligible stories
 * @param requestedBy caller id (authorization already enforced at the controller)
 */
public record PushAllStoriesCommand(UUID projectId, @Nullable List<UUID> storyIds, UUID requestedBy) {}
