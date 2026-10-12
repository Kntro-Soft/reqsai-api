package com.kntro.reqsai.discovery.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * Where in the client's code a suggestion or story is grounded. A module reference carries the repository
 * ({@code owner/name}), the module's folder, its business name and a link to it on the code host. A reference
 * found by a deep search also carries the {@code file} and its line range, and {@code url} then points at those
 * lines at the indexed commit ({@code …/blob/<sha>/<file>#L<a>-L<b>}); {@code quote} is the line that proves it.
 * The location fields are optional, so references stored before deep search still read. Stored as JSON.
 */
public record CodeReference(
        String repository,
        String path,
        String name,
        @Nullable String url,
        @Nullable String file,
        @Nullable Integer lineStart,
        @Nullable Integer lineEnd,
        @Nullable String quote
) {

    /** A reference to a module as a whole (no file or lines). */
    public CodeReference(String repository, String path, String name, @Nullable String url) {
        this(repository, path, name, url, null, null, null, null);
    }

    /** Whether it points at lines of a file rather than at a module. */
    public boolean hasLines() {
        return file != null && lineStart != null;
    }
}
