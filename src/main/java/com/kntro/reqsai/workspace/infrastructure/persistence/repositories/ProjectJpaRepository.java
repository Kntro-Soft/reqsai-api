package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<Project, UUID> {
    Page<Project> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
    boolean existsByOrganizationIdAndNameAndIdNot(UUID organizationId, String name, UUID id);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.organizationId = :organizationId AND p.status = 'ACTIVE'")
    int countActiveByOrganizationId(UUID organizationId);
}
