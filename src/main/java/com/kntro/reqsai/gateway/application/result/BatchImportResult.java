package com.kntro.reqsai.gateway.application.result;

import java.util.List;

/**
 * Aggregate result of a Jira import: per-issue {@link ImportStoryResult}s plus the counts required by the
 * locked contract. {@code imported} counts created stories, {@code skipped} counts duplicates, and
 * {@code failed} counts per-issue failures (which never abort the batch).
 */
public record BatchImportResult(int imported, int skipped, int failed, List<ImportStoryResult> results) {

    public static BatchImportResult of(List<ImportStoryResult> results) {
        int imported = (int) results.stream().filter(r -> r.status() == ImportStoryResult.Status.IMPORTED).count();
        int skipped = (int) results.stream().filter(r -> r.status() == ImportStoryResult.Status.DUPLICATE).count();
        int failed = (int) results.stream().filter(r -> r.status() == ImportStoryResult.Status.FAILED).count();
        return new BatchImportResult(imported, skipped, failed, results);
    }
}
