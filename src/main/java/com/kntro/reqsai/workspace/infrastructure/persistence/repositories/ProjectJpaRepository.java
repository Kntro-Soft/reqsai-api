package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Project> findByIdAndOrganizationIdAndStatus(UUID id, UUID organizationId, ProjectStatus status);
    Page<Project> findAllByOrganizationIdAndStatus(UUID organizationId, ProjectStatus status, Pageable pageable);
    boolean existsByOrganizationIdAndNameAndStatus(UUID organizationId, String name, ProjectStatus status);
    boolean existsByOrganizationIdAndNameAndIdNotAndStatus(UUID organizationId, String name, UUID id, ProjectStatus status);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.organizationId = :organizationId AND p.status = 'ACTIVE'")
    int countActiveByOrganizationId(UUID organizationId);
}
