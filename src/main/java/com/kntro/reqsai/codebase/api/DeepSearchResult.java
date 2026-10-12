package com.kntro.reqsai.codebase.api;

import java.util.List;

/**
 * What a deep search of the client's code found: the best snippets, bounded in size, best first.
 *
 * @param snippets       the passages, best first; empty when nothing in the code relates to the query
 * @param repositories   the repositories that were searched ({@code owner/name})
 * @param filesScanned   how many source files were read
 * @param timedOut       whether the time budget ran out before every repository was searched
 */
public record DeepSearchResult(List<CodeSnippet> snippets, List<String> repositories, int filesScanned,
                               boolean timedOut) {

    public DeepSearchResult {
        snippets = snippets == null ? List.of() : List.copyOf(snippets);
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }

    public static DeepSearchResult empty() {
        return new DeepSearchResult(List.of(), List.of(), 0, false);
    }
}
