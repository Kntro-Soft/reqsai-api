package com.kntro.reqsai.codebase.infrastructure.persistence.repositories;

import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeRepositoryJpaRepository extends JpaRepository<CodeRepository, UUID> {

    Optional<CodeRepository> findByIdAndProjectId(UUID id, UUID projectId);

    List<CodeRepository> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);

    @Query("select count(r) > 0 from CodeRepository r where r.projectId = :projectId"
            + " and lower(r.owner) = lower(:owner) and lower(r.name) = lower(:name)")
    boolean existsByProjectIdAndFullName(@Param("projectId") UUID projectId, @Param("owner") String owner,
                                         @Param("name") String name);
}
