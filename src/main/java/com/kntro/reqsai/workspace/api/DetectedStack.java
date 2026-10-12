package com.kntro.reqsai.workspace.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/** The technology found in the project's indexed repositories, to offer as its technical profile. */
public record DetectedStack(List<String> languages, List<String> frameworks, List<String> databases,
                            List<String> platforms) {

    public DetectedStack {
        languages = languages == null ? List.of() : List.copyOf(languages);
        frameworks = frameworks == null ? List.of() : List.copyOf(frameworks);
        databases = databases == null ? List.of() : List.copyOf(databases);
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return languages.isEmpty() && frameworks.isEmpty() && databases.isEmpty() && platforms.isEmpty();
    }
}
