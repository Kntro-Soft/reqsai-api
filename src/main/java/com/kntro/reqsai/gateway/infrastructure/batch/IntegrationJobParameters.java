package com.kntro.reqsai.gateway.infrastructure.batch;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Job-parameter keys shared by the integration batch jobs. Spring Batch derives the
 * <em>JobInstance</em> identity from the job name plus the <strong>identifying</strong> parameters —
 * here only {@link #DOMAIN_JOB_ID} (the {@code integration_sync_jobs} UUID) is identifying, so every
 * API-triggered run is a fresh JobInstance with exactly one JobExecution, and the batch metadata
 * links 1:1 back to the domain row. The remaining keys are non-identifying context the execution
 * needs: the tenant coordinates to restore ({@link #TENANT_ID}/{@link #TENANT_SCHEMA}), the project,
 * and the optional issue-key selection.
 */
public final class IntegrationJobParameters {

    /** Identifying: the {@code integration_sync_jobs} row this execution reports into. */
    public static final String DOMAIN_JOB_ID = "domainJobId";

    public static final String PROJECT_ID = "projectId";
    public static final String TENANT_ID = "tenantId";
    public static final String TENANT_SCHEMA = "tenantSchema";

    /** Comma-joined Jira issue keys to import; absent/blank means all eligible issues. */
    public static final String ISSUE_KEYS = "issueKeys";

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
}
