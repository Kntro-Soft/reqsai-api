package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link UserStory} aggregate. Tenant-scoped (schema resolved from the JWT
 * {@code orgId}).
 */
public interface UserStoryRepository {

    UserStory save(UserStory story);

    Optional<UserStory> findById(UUID id);

    /**
     * Finds a story by id and validates it belongs to the given project.
     * Used by criterion handlers to scope-check in a single call.
     */
    Optional<UserStory> findByIdAndProjectId(UUID storyId, UUID projectId);

    Page<UserStory> findAllByProjectId(UUID projectId, Pageable pageable);

    Page<UserStory> findAllBySessionId(UUID sessionId, Pageable pageable);

    void deleteAllBySessionId(UUID sessionId);

    /**
     * Highest cosine similarity (0..1) between {@code embedding} and any already-embedded story of the
     * project, or empty when the project has none yet. Used to reject near-duplicates on creation.
     */
    Optional<Double> highestSimilarity(UUID projectId, float[] embedding);

    /**
     * Returns the story most similar to {@code embedding} in the project, together with its similarity
     * score, or empty when no embedded stories exist yet. Used by the suggestion layer to find the
     * target of an {@code UPDATE_STORY} or {@code EDGE_CASE} suggestion.
     */
    Optional<SimilarStory> findMostSimilar(UUID projectId, float[] embedding);

    record SimilarStory(UUID storyId, double similarity) {}
}
