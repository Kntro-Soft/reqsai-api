package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.UserStoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

/** Adapts the {@link UserStoryRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class UserStoryRepositoryAdapter implements UserStoryRepository {

    private final UserStoryJpaRepository jpa;

    @Override
    public UserStory save(UserStory story) {
        return jpa.save(story);
    }

    @Override
    public Optional<UserStory> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<UserStory> findByIdAndProjectId(UUID storyId, UUID projectId) {
        return jpa.findByIdAndProjectId(storyId, projectId);
    }

    @Override
    public Page<UserStory> findAllByProjectId(UUID projectId, Pageable pageable) {
        return jpa.findAllByProjectId(projectId, pageable);
    }

    @Override
    public Page<UserStory> findAllBySessionId(UUID sessionId, Pageable pageable) {
        return jpa.findAllBySessionId(sessionId, pageable);
    }

    @Override
    public void deleteAllBySessionId(UUID sessionId) {
        jpa.deleteAllBySessionId(sessionId);
    }

    @Override
    public Optional<Double> highestSimilarity(UUID projectId, float[] embedding) {
        // cosine similarity = 1 - cosine distance (pgvector <=>)
        return jpa.minCosineDistance(projectId, toVectorLiteral(embedding))
                .map(distance -> 1.0 - distance);
    }

    @Override
    public Optional<SimilarStory> findMostSimilar(UUID projectId, float[] embedding) {
        return jpa.findClosest(projectId, toVectorLiteral(embedding))
                .map(row -> {
                    UUID storyId = UUID.fromString(row[0].toString());
                    double similarity = 1.0 - ((Number) row[1]).doubleValue();
                    return new SimilarStory(storyId, similarity);
                });
    }

    /** Renders a float[] as a pgvector literal, e.g. {@code [0.12,0.34,...]}. */
    private static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
