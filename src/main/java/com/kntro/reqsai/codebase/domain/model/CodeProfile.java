package com.kntro.reqsai.codebase.domain.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The technical profile detected in a repository: languages from the source files, frameworks and
 * databases from its manifests, client platforms, and a short overview of what the product does.
 */
public record CodeProfile(
        List<String> languages,
        List<String> frameworks,
        List<String> databases,
        List<String> platforms,
        @Nullable String overview
) {

    public CodeProfile {
        languages = languages == null ? List.of() : List.copyOf(languages);
        frameworks = frameworks == null ? List.of() : List.copyOf(frameworks);
        databases = databases == null ? List.of() : List.copyOf(databases);
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
    }

    public static CodeProfile empty() {
        return new CodeProfile(List.of(), List.of(), List.of(), List.of(), null);
    }

    public CodeProfile withOverview(@Nullable String text) {
        return new CodeProfile(languages, frameworks, databases, platforms, text);
    }
}
