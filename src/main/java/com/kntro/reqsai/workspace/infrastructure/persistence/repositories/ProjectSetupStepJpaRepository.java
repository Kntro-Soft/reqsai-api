package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.ProjectSetupStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectSetupStepJpaRepository extends JpaRepository<ProjectSetupStep, UUID> {

    List<ProjectSetupStep> findAllByProjectId(UUID projectId);

    Optional<ProjectSetupStep> findByProjectIdAndStepKey(UUID projectId, String stepKey);
}
