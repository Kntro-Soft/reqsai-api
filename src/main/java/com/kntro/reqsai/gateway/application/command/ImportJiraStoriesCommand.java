package com.kntro.reqsai.gateway.application.command;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Intent to import Jira issues from the project's configured target into the backlog as user stories.
 *
 * @param projectId  the project (its {@code project_integration_targets} row says WHERE to pull from)
 * @param issueKeys  the specific Jira issue keys to import; {@code null}/empty means all eligible issues
 * @param requestedBy caller id (authorization already enforced at the controller)
 */
public record ImportJiraStoriesCommand(UUID projectId, @Nullable List<String> issueKeys, UUID requestedBy) {}
