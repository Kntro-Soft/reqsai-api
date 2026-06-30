package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Project context injected into the LLM generation prompt for realtime user-story suggestions.
 * Built from a {@link ProjectSnapshot} so that Discovery never imports workspace internals.
 */
public record GenerationContext(
        String projectName,
        @Nullable String projectDescription,
        List<String> programmingLanguages,
        List<String> frameworks,
        List<String> databases,
        @Nullable String architecture,
        @Nullable String domain,
        List<String> constraints,
        List<GlossaryEntry> glossaryTerms
) {

    public record GlossaryEntry(String term, String definition) {}

    public static GenerationContext from(ProjectSnapshot snapshot) {
        return new GenerationContext(
                snapshot.name(),
                snapshot.description(),
                snapshot.programmingLanguages(),
                snapshot.frameworks(),
                snapshot.databases(),
                snapshot.architecture(),
                snapshot.domain(),
                snapshot.constraints(),
                snapshot.glossaryTerms().stream()
                        .map(t -> new GlossaryEntry(t.term(), t.definition()))
                        .toList()
        );
    }
}
