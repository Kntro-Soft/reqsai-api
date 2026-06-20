package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectConstraintSimilarityJpaRepository extends JpaRepository<ProjectConstraint, UUID> {

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            SELECT pc.* FROM project_constraints pc
            WHERE pc.project_id = :projectId AND pc.embedding IS NOT NULL
            ORDER BY pc.embedding <=> CAST(:embedding AS vector)
            """, nativeQuery = true)
    List<ProjectConstraint> findSimilarByProjectId(
            @Param("projectId") UUID projectId,
            @Param("embedding") String embedding,
            Pageable pageable
    );
}
