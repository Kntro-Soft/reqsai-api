package com.kntro.reqsai.gateway.application.command;

import java.util.UUID;

/** Push a single project story to the project's configured Jira target. */
public record PushStoryCommand(UUID projectId, UUID storyId, UUID requestedBy) {}
