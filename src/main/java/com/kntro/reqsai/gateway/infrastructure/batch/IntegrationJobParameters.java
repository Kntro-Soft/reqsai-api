package com.kntro.reqsai.gateway.infrastructure.batch;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Job-parameter keys shared by the integration batch jobs. Spring Batch derives the
 * <em>JobInstance</em> identity from the job name plus the <strong>identifying</strong> parameters —
 * here only {@link #DOMAIN_JOB_ID} (the {@code integration_sync_jobs} UUID) is identifying, so every
 * API-triggered run is a fresh JobInstance with exactly one JobExecution, and the batch metadata
 * links 1:1 back to the domain row. The remaining keys are non-identifying context the execution
 * needs: the tenant coordinates to restore ({@link #TENANT_ID}/{@link #TENANT_SCHEMA}), the project,
 * the optional issue-key selection (import) and the optional story-id selection (push-all).
 */
public final class IntegrationJobParameters {

    /** Identifying: the {@code integration_sync_jobs} row this execution reports into. */
    public static final String DOMAIN_JOB_ID = "domainJobId";

    public static final String PROJECT_ID = "projectId";
    public static final String TENANT_ID = "tenantId";
    public static final String TENANT_SCHEMA = "tenantSchema";

    /** Comma-joined Jira issue keys to import; absent/blank means all eligible issues. */
    public static final String ISSUE_KEYS = "issueKeys";

    /** Comma-joined story ids to push; absent/blank means all eligible stories. */
    public static final String STORY_IDS = "storyIds";

    private IntegrationJobParameters() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Parses the comma-joined {@link #ISSUE_KEYS} value; empty set means "no restriction". */
    public static Set<String> parseIssueKeys(String issueKeysCsv) {
        Set<String> keys = new LinkedHashSet<>();
        if (issueKeysCsv != null && !issueKeysCsv.isBlank()) {
            for (String key : issueKeysCsv.split(",")) {
                if (!key.isBlank()) {
                    keys.add(key.trim());
                }
            }
        }
        return keys;
    }

    /** Joins issue keys for the {@link #ISSUE_KEYS} parameter; {@code null} when unrestricted. */
    public static String joinIssueKeys(java.util.List<String> issueKeys) {
        if (issueKeys == null || issueKeys.isEmpty()) {
            return null;
        }
        return String.join(",", issueKeys);
    }

    /**
     * Parses the comma-joined {@link #STORY_IDS} value into an ordered set of {@link UUID}s; an empty
     * set means "no restriction" (push every eligible story). Blank or malformed tokens are ignored.
     */
    public static Set<UUID> parseStoryIds(String storyIdsCsv) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (storyIdsCsv != null && !storyIdsCsv.isBlank()) {
            for (String token : storyIdsCsv.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isBlank()) {
                    ids.add(UUID.fromString(trimmed));
                }
            }
        }
        return ids;
    }

    /** Joins story ids for the {@link #STORY_IDS} parameter; {@code null} when unrestricted. */
    public static String joinStoryIds(List<UUID> storyIds) {
        if (storyIds == null || storyIds.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (UUID id : storyIds) {
            joiner.add(id.toString());
        }
        return joiner.toString();
    }
}
