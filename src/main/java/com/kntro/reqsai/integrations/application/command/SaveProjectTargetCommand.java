package com.kntro.reqsai.integrations.application.command;

import java.util.UUID;

/** Create or replace the single Jira push target of a project. */
public record SaveProjectTargetCommand(
        UUID projectId,
        UUID connectionId,
        String jiraProjectKey,
        String issueTypeName,
        UUID requestedBy
) {}
