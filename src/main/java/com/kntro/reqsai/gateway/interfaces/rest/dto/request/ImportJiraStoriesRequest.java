package com.kntro.reqsai.gateway.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Request body for the Jira import. {@code issueKeys} restricts the import to the given issue keys; omit or
 * leave empty to import every eligible issue of the project's target.
 */
@Schema(description = "Request body to import Jira issues as user stories")
public record ImportJiraStoriesRequest(
        @Schema(description = "Specific Jira issue keys to import; omit/empty = all eligible", example = "[\"PAY-1\",\"PAY-2\"]")
        @Nullable List<String> issueKeys
) {}
