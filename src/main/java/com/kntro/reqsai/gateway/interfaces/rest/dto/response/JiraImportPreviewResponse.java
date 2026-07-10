package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Preview of a Jira import: candidate issues with likely-duplicate flags. */
@Schema(description = "Candidate Jira issues eligible for import, with likely-duplicate flags")
public record JiraImportPreviewResponse(int total, List<Candidate> issues) {

    @Schema(description = "One candidate Jira issue")
    public record Candidate(
            String jiraIssueKey,
            String summary,
            @Nullable String issueType,
            boolean duplicate,
            @Nullable UUID existingStoryId) {}
}
