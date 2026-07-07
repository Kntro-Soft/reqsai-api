package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Gives stories that were persisted without an embedding a second chance to enter the vector index.
 *
 * <p>A story ends up un-indexed ({@link UserStory#isIndexed()} {@code == false}) when the embedding
 * provider was unavailable or failed at write time — e.g. the best-effort embed inside suggestion
 * accept. Un-indexed stories are invisible to similarity search (context retrieval, dedup,
 * UPDATE_STORY target resolution), so left alone they would stay invisible forever.
 *
 * <p><strong>Chosen approach: lazy, batched, best-effort.</strong> {@link #reindexPending(UUID)} is
 * invoked from the realtime suggestion pipeline right before each vector search — the exact moment
 * indexing matters — rather than by a scheduler. Each pass embeds at most {@link #BATCH_SIZE}
 * stories (oldest first); a failing provider aborts the pass silently, and the next generation
 * trigger retries. No new infrastructure, no extra failure mode on any user-facing path.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserStoryReindexService {

    static final int BATCH_SIZE = 10;

    private final UserStoryRepository stories;
    private final EmbeddingPort embeddingPort;

    /**
     * Embeds and saves up to {@link #BATCH_SIZE} un-indexed stories of the project. Best-effort:
     * never throws; stops the batch on the first provider failure (the provider is likely down —
     * hammering it per story is pointless).
     */
    public void reindexPending(UUID projectId) {
        if (!embeddingPort.isAvailable()) {
            return;
        }
        List<UserStory> pending = stories.findUnindexedByProjectId(projectId, BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }
        int reindexed = 0;
        for (UserStory story : pending) {
            try {
                story.assignEmbedding(embeddingPort.embed(story.toCanonicalText()));
                stories.save(story);
                reindexed++;
            } catch (RuntimeException e) {
                log.warn("Re-indexing story {} of project {} failed; aborting this pass: {}",
                        story.getId(), projectId, e.getMessage());
                break;
            }
        }
        if (reindexed > 0) {
            log.info("Re-indexed {}/{} previously un-indexed stories for project {}",
                    reindexed, pending.size(), projectId);
        }
    }
}
