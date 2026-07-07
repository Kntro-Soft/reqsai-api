package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** A project's Jira push target. */
@Schema(description = "The Jira push target configured for a project")
public record ProjectJiraTargetResponse(
        UUID id,
        UUID projectId,
        UUID connectionId,
        String jiraProjectKey,
        String issueTypeName,
        Instant createdAt,
        Instant updatedAt
) {}
