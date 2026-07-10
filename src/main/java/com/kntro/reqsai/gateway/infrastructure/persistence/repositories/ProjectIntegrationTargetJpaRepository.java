package com.kntro.reqsai.gateway.infrastructure.persistence.repositories;

import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectIntegrationTargetJpaRepository extends JpaRepository<ProjectIntegrationTarget, UUID> {

    Optional<ProjectIntegrationTarget> findByProjectId(UUID projectId);
}
