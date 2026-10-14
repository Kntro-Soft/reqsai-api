package com.kntro.reqsai.codebase.application.result;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A proposed profile for a new project. {@code generated} is false when the AI could not be used and the
 * proposal only repeats what the sources' metadata says.
 */
public record ProjectProfileProposal(boolean generated, @Nullable String name, @Nullable String description,
                                     @Nullable String domain, @Nullable String architecture,
                                     List<String> programmingLanguages, List<String> frameworks,
                                     List<String> clientPlatforms, List<String> databases) {
}
