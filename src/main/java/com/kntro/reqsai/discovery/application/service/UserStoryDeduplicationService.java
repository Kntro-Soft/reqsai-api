package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guards against near-duplicate user stories within the same project using cosine similarity
 * over pgvector embeddings. Shared by every handler that creates or generates stories, so the
 * deduplication logic lives in one place.
 * <p>
 * When no embedding model is configured ({@code ai.model.embedding=none}) the check is skipped
 * and the story is saved without an embedding (graceful degradation).
 */
@Component
@RequiredArgsConstructor
public class UserStoryDeduplicationService {

    private final UserStoryRepository stories;
    private final EmbeddingPort embeddingPort;

    /**
     * Embeds the story's canonical text, checks it against existing project stories and assigns
     * the embedding. Throws if a near-duplicate is found (similarity ≥ {@link UserStory#DUPLICATE_THRESHOLD}).
     * No-op when the embedding port is unavailable.
     */
    public void embedAndGuardDuplicates(UserStory story) {
        if (!embeddingPort.isAvailable()) return;
        float[] embedding = embeddingPort.embed(story.toCanonicalText());
        stories.highestSimilarity(story.getProjectId(), embedding)
                .filter(sim -> sim >= UserStory.DUPLICATE_THRESHOLD)
                .ifPresent(sim -> { throw DiscoveryExceptions.duplicateUserStory(sim); });
        story.assignEmbedding(embedding);
    }
}
