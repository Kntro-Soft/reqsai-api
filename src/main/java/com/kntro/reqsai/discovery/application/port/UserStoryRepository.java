package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.application.query.StoryFilter;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    /**
     * Paginated project backlog with optional server-side {@link StoryFilter filters} (text search over
     * title/role/action, status, priority, {@code createdAt} range). An empty filter behaves exactly
     * like {@link #findAllByProjectId(UUID, Pageable)}. Filtering runs in the database, never in memory,
     * so total counts and paging stay correct across large backlogs.
     */
    Page<UserStory> findAllByProjectId(UUID projectId, StoryFilter filter, Pageable pageable);

    Page<UserStory> findAllBySessionId(UUID sessionId, Pageable pageable);

    /**
     * Returns the stories of {@code projectId} whose id is in {@code storyIds}, in an arbitrary order.
     * Used by the batch delete to resolve the candidate ids to managed aggregates: ids not belonging to
     * the project simply do not appear in the result (silently skipped).
     */
    List<UserStory> findAllByProjectIdAndIdIn(UUID projectId, List<UUID> storyIds);

    /**
     * Permanently deletes the story (hard delete, mirroring document deletion). Removing the aggregate
     * cascades to its acceptance criteria via {@code orphanRemoval}. This is a local delete only: it does
     * not touch any external tracker (e.g. Jira) issue the story was exported to.
     */
    void delete(UserStory story);

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

    /**
     * Returns up to {@code limit} indexed stories of the project ordered by ascending cosine distance
     * to {@code embedding}. Used to ground the realtime generation prompt in the most relevant part
     * of the backlog. Empty when the project has no indexed stories.
     */
    List<UserStory> findTopSimilar(UUID projectId, float[] embedding, int limit);

    /**
     * Returns up to {@code limit} of the project's stories whose cosine similarity to {@code embedding}
     * is at least {@code minSimilarity}, nearest first, each paired with its similarity score. The
     * threshold is intended to be LOOSE (a recall floor well below the auto-dedup bar) so paraphrase
     * candidates that sit at cosine 0.55–0.82 are surfaced to the LLM as candidate existing stories to
     * update/dedup against, rather than being silently missed by the strict embedding gate. Empty when
     * the project has no indexed story within the floor.
     */
    List<SimilarStory> findSimilarCandidates(UUID projectId, float[] embedding, double minSimilarity, int limit);

    /**
     * Returns up to {@code limit} stories of the project, newest first. Embedding-independent
     * fallback (and in-session recency complement) for the generation context, so the LLM always
     * sees the backlog even when vector search is unavailable or empty.
     */
    List<UserStory> findRecentByProjectId(UUID projectId, int limit);

    /**
     * Returns up to {@code limit} stories of the project persisted without an embedding (the
     * provider was down or failed at write time). Feeds the lazy re-index pass that gives those
     * stories a second chance to enter the vector index.
     */
    List<UserStory> findUnindexedByProjectId(UUID projectId, int limit);

    record SimilarStory(UUID storyId, double similarity) {}
}
