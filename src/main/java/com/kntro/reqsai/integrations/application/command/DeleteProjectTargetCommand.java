package com.kntro.reqsai.integrations.application.command;

import java.util.UUID;

/** Delete a project's Jira push target. */
public record DeleteProjectTargetCommand(UUID projectId, UUID requestedBy) {}
