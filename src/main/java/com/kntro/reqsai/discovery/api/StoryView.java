package com.kntro.reqsai.discovery.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of a {@code UserStory} exposed by Discovery via {@link DiscoveryStoryReadPort}.
 * Carries only the text fields another module needs to render the story into an external tracker issue
 * (title, role/action/benefit, priority, story points and the Given/When/Then acceptance criteria).
 *
 * <p>{@code priority} is the {@code Priority} enum name; no JPA entities, no embeddings cross this
 * boundary.
 */
public record StoryView(
        UUID storyId,
        UUID projectId,
        String title,
        String role,
        String action,
        String benefit,
        String priority,
        @Nullable Integer storyPoints,
        List<AcceptanceCriterionView> acceptanceCriteria
) {}
