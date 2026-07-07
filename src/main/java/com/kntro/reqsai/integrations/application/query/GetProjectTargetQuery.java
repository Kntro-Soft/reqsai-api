package com.kntro.reqsai.integrations.application.query;

import java.util.UUID;

/** Read a project's Jira push target. */
public record GetProjectTargetQuery(UUID projectId, UUID requestedBy) {}
