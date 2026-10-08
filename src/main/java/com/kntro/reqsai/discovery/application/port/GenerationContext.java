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
 *   <li>{@link #alreadySuggested()} — this session's suggestions still pending analyst review, each
 *       with its id, so the model does not re-suggest what it just suggested and CAN target a pending
 *       item (e.g. refine an as-yet-unreviewed story draft) instead of spawning a near-duplicate.</li>
 *   <li>{@link #documents()} — the context summaries of the client documents the analyst uploaded and
 *       applied (newest first), so the model knows the client's business beyond the meeting.</li>
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
        List<PendingSuggestion> alreadySuggested,
        List<DocumentEntry> documents
) {

    public GenerationContext {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /** A context without client documents. */
    public GenerationContext(String projectName, @Nullable String projectDescription,
                             List<String> programmingLanguages, List<String> frameworks, List<String> databases,
                             @Nullable String architecture, @Nullable String domain, List<String> constraints,
                             List<GlossaryEntry> glossaryTerms, List<StorySummary> existingStories,
                             List<PendingSuggestion> alreadySuggested) {
        this(projectName, projectDescription, programmingLanguages, frameworks, databases, architecture, domain,
                constraints, glossaryTerms, existingStories, alreadySuggested, List.of());
    }

    public record GlossaryEntry(String term, String definition) {}

    /** The analyst-approved context summary of a client document uploaded to the project (US22). */
    public record DocumentEntry(String name, String summary) {}

    /** Compact view of an existing backlog story, id included so the LLM can target it. */
    public record StorySummary(UUID id, String title, String role, String action, String benefit) {}

    /**
     * A still-PENDING suggestion of this session, id included so the LLM can point an
     * {@code UPDATE_STORY}/{@code EDGE_CASE} at it instead of re-emitting a near-duplicate NEW_STORY.
     */
    public record PendingSuggestion(UUID id, String summary) {}

    public static GenerationContext from(ProjectSnapshot snapshot) {
        return from(snapshot, List.of(), List.of());
    }

    public static GenerationContext from(ProjectSnapshot snapshot,
                                         List<StorySummary> existingStories,
                                         List<PendingSuggestion> alreadySuggested) {
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
                List.copyOf(alreadySuggested),
                snapshot.documents().stream()
                        .map(d -> new DocumentEntry(d.name(), d.summary()))
                        .toList()
        );
    }
}
