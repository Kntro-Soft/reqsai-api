package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectSetupStepRepository;
import com.kntro.reqsai.workspace.domain.model.ProjectSetupStep;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectSetupStepJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link ProjectSetupStepRepository} port to Spring Data JPA. */
@Component
@RequiredArgsConstructor
public class ProjectSetupStepRepositoryAdapter implements ProjectSetupStepRepository {

    private final ProjectSetupStepJpaRepository jpa;

    @Override
    public ProjectSetupStep save(ProjectSetupStep step) {
        return jpa.save(step);
    }

    @Override
    public List<ProjectSetupStep> findAllByProjectId(UUID projectId) {
        return jpa.findAllByProjectId(projectId);
    }

    @Override
    public Optional<ProjectSetupStep> findByProjectIdAndStepKey(UUID projectId, String stepKey) {
        return jpa.findByProjectIdAndStepKey(projectId, stepKey);
    }
}
