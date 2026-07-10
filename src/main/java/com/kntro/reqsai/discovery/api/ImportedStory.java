package com.kntro.reqsai.discovery.api;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Outcome of importing one external issue through {@link DiscoveryStoryWritePort}: either a new story was
 * created, or the transformed story was detected as a near-duplicate of an existing one (reusing the same
 * similarity/deduplication gate as manual and AI-generated creation) and therefore <strong>not</strong>
 * created.
 *
 * @param status         {@link Status#CREATED} or {@link Status#DUPLICATE}
 * @param storyId        the created story id when {@code CREATED}, else {@code null}
 * @param existingStoryId the near-duplicate's story id when {@code DUPLICATE} and it could be resolved,
 *                        else {@code null}
 * @param similarity     cosine similarity to the existing story when {@code DUPLICATE} (0 otherwise)
 */
public record ImportedStory(
        Status status,
        @Nullable UUID storyId,
        @Nullable UUID existingStoryId,
        double similarity
) {

    public enum Status { CREATED, DUPLICATE }

    public static ImportedStory created(UUID storyId) {
        return new ImportedStory(Status.CREATED, storyId, null, 0.0);
    }

    public static ImportedStory duplicate(@Nullable UUID existingStoryId, double similarity) {
        return new ImportedStory(Status.DUPLICATE, null, existingStoryId, similarity);
    }

    public boolean isDuplicate() {
        return status == Status.DUPLICATE;
    }
}
