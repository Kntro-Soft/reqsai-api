package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link UserStory} (tenant-scoped table {@code user_stories}). */
public interface UserStoryJpaRepository extends JpaRepository<UserStory, UUID> {

    Page<UserStory> findAllByProjectId(UUID projectId, Pageable pageable);

    Page<UserStory> findAllBySessionId(UUID sessionId, Pageable pageable);

    Optional<UserStory> findByIdAndProjectId(UUID id, UUID projectId);

    void deleteAllBySessionId(UUID sessionId);

    /**
     * Smallest pgvector cosine <em>distance</em> ({@code <=>}, in {@code [0,2]}) between the given
     * vector literal and any embedded story of the project. Native because pgvector operators are not
     * HQL; runs on the tenant connection so {@code search_path} already targets the right schema.
     */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select min(embedding <=> cast(:embedding as vector))
            from user_stories
            where project_id = :projectId and embedding is not null
            """, nativeQuery = true)
    Optional<Double> minCosineDistance(@Param("projectId") UUID projectId, @Param("embedding") String embedding);

    /**
     * Returns the {@code (id, distance)} pair for the story that is closest (smallest cosine
     * distance) to the given vector within the project, or empty when no embedded stories exist.
     * Uses a {@code LIMIT 1} scan so the database only materialises one row.
     */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, (embedding <=> cast(:embedding as vector)) as dist
            from user_stories
            where project_id = :projectId and embedding is not null
            order by dist
            limit 1
            """, nativeQuery = true)
    List<Object[]> findClosest(@Param("projectId") UUID projectId, @Param("embedding") String embedding);

    /**
     * The {@code limit} stories closest (smallest cosine distance) to the given vector within the
     * project, ordered nearest-first. Feeds the realtime generation context with the slice of the
     * backlog most relevant to the recent transcript.
     */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select * from user_stories
            where project_id = :projectId and embedding is not null
            order by embedding <=> cast(:embedding as vector)
            limit :limit
            """, nativeQuery = true)
    List<UserStory> findTopSimilar(@Param("projectId") UUID projectId,
                                   @Param("embedding") String embedding,
                                   @Param("limit") int limit);
}
