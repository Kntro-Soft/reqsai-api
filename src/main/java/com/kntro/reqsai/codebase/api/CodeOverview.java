package com.kntro.reqsai.codebase.api;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** What the project's indexed repositories say about the product as a whole. */
public record CodeOverview(
        List<String> repositories,
        @Nullable String overview,
        List<String> languages,
        List<String> frameworks,
        List<String> databases,
        List<String> platforms
) {
}
