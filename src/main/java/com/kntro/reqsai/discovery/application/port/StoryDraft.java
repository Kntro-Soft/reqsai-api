package com.kntro.reqsai.discovery.application.port;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The text of a story as a plain value: what the model reads to improve it, what it proposes back and what the
 * analyst applies. It is not a {@code UserStory}: nothing in it is stored until the proposal is applied.
 */
public record StoryDraft(
        String title,
        String role,
        String action,
        String benefit,
        List<CriterionDraft> acceptanceCriteria
) {

    public StoryDraft {
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }

    /** One Given / When / Then acceptance criterion; the scenario label is optional. */
    public record CriterionDraft(@Nullable String scenario, String given, String when, String then) {
    }
}
