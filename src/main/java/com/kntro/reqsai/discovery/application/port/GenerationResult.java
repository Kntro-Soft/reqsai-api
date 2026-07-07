package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Value object returned by {@link RequirementGenerationPort} after AI extraction.
 *
 * <p>The {@code stories} list is used by the <strong>batch</strong> path (direct persistence) and
 * by the <strong>realtime suggestion</strong> path (pending review). Each story now carries a
 * {@link SuggestionType} so the realtime path can classify it without a second LLM call:
 * <ul>
 *   <li>{@code NEW_STORY} — new requirement (server may upgrade to {@code UPDATE_STORY} via embedding).</li>
 *   <li>{@code UPDATE_STORY} — refines/extends an existing backlog story shown in the prompt context;
 *       {@code targetStoryId} carries the story id the LLM picked (validated server-side).</li>
 *   <li>{@code EDGE_CASE} — boundary scenario; {@code targetStoryId} (when the LLM picked one) or the
 *       {@code relatedTopic} hint helps the server locate the target story.</li>
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
            @Nullable String relatedTopic,
            @Nullable UUID targetStoryId
    ) {
        /** Convenience constructor for the batch path (always NEW_STORY, no relatedTopic/target). */
        public GeneratedStory(String title, String role, String action, String benefit,
                              Priority priority, @Nullable Integer storyPoints,
                              List<GeneratedCriterion> acceptanceCriteria) {
            this(SuggestionType.NEW_STORY, title, role, action, benefit,
                 priority, storyPoints, acceptanceCriteria, null, null);
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
