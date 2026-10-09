package com.kntro.reqsai.codebase.infrastructure.persistence.repositories;

import com.kntro.reqsai.codebase.domain.model.CodeModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeModuleJpaRepository extends JpaRepository<CodeModule, UUID> {

    List<CodeModule> findAllByRepositoryIdOrderByPathAsc(UUID repositoryId);

    List<CodeModule> findAllByProjectIdOrderByPathAsc(UUID projectId);

    Optional<CodeModule> findByRepositoryIdAndPath(UUID repositoryId, String path);

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            SELECT m.* FROM code_modules m
            WHERE m.project_id = :projectId AND m.embedding IS NOT NULL
            ORDER BY m.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<CodeModule> findNearest(@Param("projectId") UUID projectId, @Param("embedding") String embedding,
                                 @Param("limit") int limit);

    @Modifying
    @Query("delete from CodeModule m where m.repositoryId = :repositoryId and m.path not in :paths")
    void deleteByRepositoryIdAndPathNotIn(@Param("repositoryId") UUID repositoryId,
                                          @Param("paths") Collection<String> paths);

    @Modifying
    @Query("delete from CodeModule m where m.repositoryId = :repositoryId")
    void deleteByRepositoryId(@Param("repositoryId") UUID repositoryId);
}
