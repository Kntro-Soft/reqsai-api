package com.kntro.reqsai.integrations.application.result;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Result of pushing a single story. On success {@code jiraIssueKey}/{@code jiraIssueUrl} are set and
 * {@code error} is null; on failure (in a batch push) {@code error} carries the error code and the Jira
 * fields are null.
 */
public record StoryPushResult(
        UUID storyId,
        @Nullable String jiraIssueKey,
        @Nullable String jiraIssueUrl,
        @Nullable String error
) {
    public static StoryPushResult success(UUID storyId, String key, String url) {
        return new StoryPushResult(storyId, key, url, null);
    }

    public static StoryPushResult failure(UUID storyId, String errorCode) {
        return new StoryPushResult(storyId, null, null, errorCode);
    }

    public boolean isSuccess() {
        return error == null;
    }
}
