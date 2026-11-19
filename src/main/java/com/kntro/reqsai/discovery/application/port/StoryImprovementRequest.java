package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the model is given to improve one story.
 *
 * @param story        the story as it is now
 * @param findings     the quality characteristics the story does not meet yet, with what was observed and how to
 *                     fix it (empty when it meets them all)
 * @param context      the project's description, domain, glossary, constraints and client documents nearest to the
 *                     story, or {@code null} when the project has none
 * @param sourceQuotes the verbatim fragments of the meetings the story comes from, so the proposal stays faithful
 *                     to what the client said (empty for a manual story)
 */
public record StoryImprovementRequest(
        StoryDraft story,
        List<Finding> findings,
        @Nullable GenerationContext context,
        List<String> sourceQuotes
) {

    public StoryImprovementRequest {
        findings = findings == null ? List.of() : List.copyOf(findings);
        sourceQuotes = sourceQuotes == null ? List.of() : List.copyOf(sourceQuotes);
    }

    /** A quality characteristic the story does not meet: what the evaluation observed and how to fix it. */
    public record Finding(String characteristic, String observation, @Nullable String suggestion) {
    }
}
