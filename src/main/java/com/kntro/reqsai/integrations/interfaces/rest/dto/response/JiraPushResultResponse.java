package com.kntro.reqsai.integrations.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Result of pushing one story to Jira. {@code error} is set (and the Jira fields null) on failure. */
@Schema(description = "Result of pushing a single story to Jira")
public record JiraPushResultResponse(
        UUID storyId,
        @Nullable String jiraIssueKey,
        @Nullable String jiraIssueUrl,
        @Nullable String error
) {}
