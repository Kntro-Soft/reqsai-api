package com.kntro.reqsai.discovery.api;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Result of checking whether a candidate story (built from an external issue) would be a near-duplicate of
 * an existing project story <strong>without creating anything</strong>. Used by the import preview so the
 * caller can flag likely duplicates before the user commits to importing.
 *
 * @param duplicate       true when the candidate's similarity to an existing story is at/above the same
 *                        deduplication threshold that creation enforces
 * @param existingStoryId the most-similar existing story id when one was found, else {@code null}
 * @param similarity      cosine similarity to that story (0 when none / embedding unavailable)
 */
public record StoryDuplicateCheck(
        boolean duplicate,
        @Nullable UUID existingStoryId,
        double similarity
) {

    public static StoryDuplicateCheck notDuplicate() {
        return new StoryDuplicateCheck(false, null, 0.0);
    }
}
