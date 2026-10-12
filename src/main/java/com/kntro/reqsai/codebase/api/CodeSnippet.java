package com.kntro.reqsai.codebase.api;

/**
 * A few lines of the client's source code found by a deep search: where they are (repository, commit, file and
 * line range, with the GitHub link to them) and the lines themselves, with secrets already removed. Never
 * stored: it only lives for the request that asked for it.
 */
public record CodeSnippet(
        String repository,
        String commitSha,
        String path,
        int lineStart,
        int lineEnd,
        String text,
        String url,
        double score
) {
}
