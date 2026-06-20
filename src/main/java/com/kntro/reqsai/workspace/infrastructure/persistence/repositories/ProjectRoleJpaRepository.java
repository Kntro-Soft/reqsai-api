package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRoleJpaRepository extends JpaRepository<ProjectRole, UUID> {
    Optional<ProjectRole> findByIdAndProjectId(UUID id, UUID projectId);
    List<ProjectRole> findAllByProjectId(UUID projectId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM ProjectRole r
            WHERE r.projectId = :projectId
              AND lower(r.name) = lower(:name)
            """)
    boolean existsByProjectIdAndName(UUID projectId, String name);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM ProjectRole r
            WHERE r.projectId = :projectId
              AND lower(r.name) = lower(:name)
              AND r.id <> :id
            """)
    boolean existsByProjectIdAndNameAndIdNot(UUID projectId, String name, UUID id);
}
