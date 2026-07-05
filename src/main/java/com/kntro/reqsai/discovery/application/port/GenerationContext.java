package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Project context injected into the LLM generation prompt for realtime user-story suggestions.
 * Built from a {@link ProjectSnapshot} so that Discovery never imports workspace internals.
 *
 * <p>Beyond the static project profile, the context grounds the model in the current backlog:
 * <ul>
 *   <li>{@link #existingStories()} — the stories most relevant to the recent transcript (vector
 *       search when available, most-recent fallback otherwise), each with its id so the model can
 *       emit {@code UPDATE_STORY}/{@code EDGE_CASE} suggestions pointing at a real story.</li>
 *   <li>{@link #alreadySuggested()} — titles/questions of this session's suggestions still pending
 *       analyst review, so the model does not re-suggest what it just suggested.</li>
 * </ul>
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
        List<GlossaryEntry> glossaryTerms,
        List<StorySummary> existingStories,
        List<String> alreadySuggested
) {

    public record GlossaryEntry(String term, String definition) {}

    /** Compact view of an existing backlog story, id included so the LLM can target it. */
    public record StorySummary(UUID id, String title, String role, String action, String benefit) {}

    public static GenerationContext from(ProjectSnapshot snapshot) {
        return from(snapshot, List.of(), List.of());
    }

    public static GenerationContext from(ProjectSnapshot snapshot,
                                         List<StorySummary> existingStories,
                                         List<String> alreadySuggested) {
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
                        .toList(),
                List.copyOf(existingStories),
                List.copyOf(alreadySuggested)
        );
    }
}
