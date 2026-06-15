package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.UserStory;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link UserStory} aggregate. Tenant-scoped (schema resolved from the JWT
 * {@code orgId}). Read methods (findById, list…) are added with their use cases.
 */
public interface UserStoryRepository {

    UserStory save(UserStory story);

    /**
     * Highest cosine similarity (0..1) between {@code embedding} and any already-embedded story of the
     * project, or empty when the project has none yet. Used to reject near-duplicates on creation.
     */
    Optional<Double> highestSimilarity(UUID projectId, float[] embedding);
}
