package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.UserStoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
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
        return jpa.findClosest(projectId, toVectorLiteral(embedding)).stream()
                .findFirst()
                .map(row -> {
                    UUID storyId = UUID.fromString(row[0].toString());
                    double similarity = 1.0 - ((Number) row[1]).doubleValue();
                    return new SimilarStory(storyId, similarity);
                });
    }

    @Override
    public List<UserStory> findTopSimilar(UUID projectId, float[] embedding, int limit) {
        return jpa.findTopSimilar(projectId, toVectorLiteral(embedding), limit);
    }

    @Override
    public List<SimilarStory> findSimilarCandidates(UUID projectId, float[] embedding,
                                                    double minSimilarity, int limit) {
        // pgvector <=> is cosine distance in [0,2]; similarity = 1 - distance, so a similarity floor of
        // minSimilarity is a distance ceiling of (1 - minSimilarity).
        double maxDistance = 1.0 - minSimilarity;
        return jpa.findSimilarWithin(projectId, toVectorLiteral(embedding), maxDistance, limit).stream()
                .map(row -> {
                    UUID storyId = UUID.fromString(row[0].toString());
                    double similarity = 1.0 - ((Number) row[1]).doubleValue();
                    return new SimilarStory(storyId, similarity);
                })
                .toList();
    }

    @Override
    public List<UserStory> findRecentByProjectId(UUID projectId, int limit) {
        return jpa.findAllByProjectId(projectId,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    @Override
    public List<UserStory> findUnindexedByProjectId(UUID projectId, int limit) {
        return jpa.findAllByProjectIdAndEmbeddingIsNull(projectId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "createdAt")));
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
