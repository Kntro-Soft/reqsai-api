package com.kntro.reqsai.gateway.application.result;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Result of importing one Jira issue. {@code status} is one of {@code imported} / {@code duplicate} /
 * {@code failed}:
 * <ul>
 *   <li>{@code imported} — a story was created; {@code storyId} is set.</li>
 *   <li>{@code duplicate} — the issue mapped to a near-duplicate of an existing story and was skipped
 *       (nothing created); {@code storyId} is null.</li>
 *   <li>{@code failed} — the issue could not be imported; {@code message} carries a token-free reason.</li>
 * </ul>
 */
public record ImportStoryResult(
        String jiraIssueKey,
        @Nullable UUID storyId,
        Status status,
        @Nullable String message
) {

    public enum Status {
        IMPORTED("imported"),
        DUPLICATE("duplicate"),
        FAILED("failed");

        private final String wire;

        Status(String wire) {
            this.wire = wire;
        }

        /** Lowercase wire value used in the locked API contract. */
        public String wire() {
            return wire;
        }
    }

    public static ImportStoryResult imported(String jiraIssueKey, UUID storyId) {
        return new ImportStoryResult(jiraIssueKey, storyId, Status.IMPORTED, null);
    }

    public static ImportStoryResult duplicate(String jiraIssueKey) {
        return new ImportStoryResult(jiraIssueKey, null, Status.DUPLICATE, null);
    }

    public static ImportStoryResult failed(String jiraIssueKey, String message) {
        return new ImportStoryResult(jiraIssueKey, null, Status.FAILED, message);
    }
}
