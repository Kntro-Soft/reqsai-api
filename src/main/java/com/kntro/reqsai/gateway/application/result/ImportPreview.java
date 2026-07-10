package com.kntro.reqsai.gateway.application.result;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Preview of a Jira import: the candidate issues eligible for import, each flagged as a likely duplicate
 * (detected via the discovery similarity path WITHOUT creating anything).
 */
public record ImportPreview(int total, List<Candidate> issues) {

    public static ImportPreview of(List<Candidate> issues) {
        return new ImportPreview(issues.size(), issues);
    }

    /**
     * One candidate issue. {@code duplicate} is true when its mapped story would collide with an existing
     * story; {@code existingStoryId} carries that story's id when resolved.
     */
    public record Candidate(
            String jiraIssueKey,
            String summary,
            @Nullable String issueType,
            boolean duplicate,
            @Nullable UUID existingStoryId
    ) {}
}
