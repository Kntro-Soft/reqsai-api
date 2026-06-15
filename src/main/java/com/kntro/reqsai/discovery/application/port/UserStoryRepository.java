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

    Page<UserStory> findAllByProjectId(UUID projectId, Pageable pageable);

    Page<UserStory> findAllBySessionId(UUID sessionId, Pageable pageable);

    /**
     * Highest cosine similarity (0..1) between {@code embedding} and any already-embedded story of the
     * project, or empty when the project has none yet. Used to reject near-duplicates on creation.
     */
    Optional<Double> highestSimilarity(UUID projectId, float[] embedding);
}
