package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Result of a Jira import: per-issue results plus imported/skipped/failed counts. */
@Schema(description = "Result of importing Jira issues as user stories")
public record JiraImportResponse(int imported, int skipped, int failed, List<Result> results) {

    @Schema(description = "Per-issue import result; status is imported | duplicate | failed")
    public record Result(
            String jiraIssueKey,
            @Nullable UUID storyId,
            String status,
            @Nullable String message) {}
}
