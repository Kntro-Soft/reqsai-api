package com.kntro.reqsai.integrations.application.command;

import java.util.UUID;

/** Push every story of a project to the project's configured Jira target (per-story failures captured). */
public record PushAllStoriesCommand(UUID projectId, UUID requestedBy) {}
