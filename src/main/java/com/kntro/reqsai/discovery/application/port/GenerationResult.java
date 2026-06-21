package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Value object returned by {@link RequirementGenerationPort} after AI extraction.
 *
 * <p>The {@code stories} list is used by the <strong>batch</strong> path (direct persistence) and
 * by the <strong>realtime suggestion</strong> path (pending review). Each story now carries a
 * {@link SuggestionType} so the realtime path can classify it without a second LLM call:
 * <ul>
 *   <li>{@code NEW_STORY} — new requirement (server may upgrade to {@code UPDATE_STORY} via embedding).</li>
 *   <li>{@code EDGE_CASE} — boundary scenario; the {@code relatedTopic} hint helps the server locate
 *       the target story by embedding search.</li>
 *   <li>{@code CLARIFYING_QUESTION} — surfaced via {@code questions}, not {@code stories}.</li>
 * </ul>
 * The batch path ignores {@code type} and {@code questions}; it only processes {@code stories}.
 */
public record GenerationResult(List<GeneratedStory> stories, List<GeneratedQuestion> questions) {

    public GenerationResult(List<GeneratedStory> stories) {
        this(stories, List.of());
    }

    public record GeneratedStory(
            SuggestionType type,
            String title,
            String role,
            String action,
            String benefit,
            Priority priority,
            @Nullable Integer storyPoints,
            List<GeneratedCriterion> acceptanceCriteria,
            @Nullable String relatedTopic
    ) {
        /** Convenience constructor for the batch path (always NEW_STORY, no relatedTopic). */
        public GeneratedStory(String title, String role, String action, String benefit,
                              Priority priority, @Nullable Integer storyPoints,
                              List<GeneratedCriterion> acceptanceCriteria) {
            this(SuggestionType.NEW_STORY, title, role, action, benefit,
                 priority, storyPoints, acceptanceCriteria, null);
        }
    }

    public record GeneratedCriterion(
            @Nullable String scenario,
            String given,
            String when,
            String then
    ) {}

    /** A clarifying question emitted by the LLM when the transcript is ambiguous. */
    public record GeneratedQuestion(String question) {}
}
