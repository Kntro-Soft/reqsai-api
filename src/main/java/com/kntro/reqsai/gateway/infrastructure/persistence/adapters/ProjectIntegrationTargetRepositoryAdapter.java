package com.kntro.reqsai.gateway.infrastructure.persistence.adapters;

import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.gateway.infrastructure.persistence.repositories.ProjectIntegrationTargetJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link ProjectIntegrationTargetRepository} port to Spring Data JPA. */
@Component
@RequiredArgsConstructor
public class ProjectIntegrationTargetRepositoryAdapter implements ProjectIntegrationTargetRepository {

    private final ProjectIntegrationTargetJpaRepository jpa;

    @Override
    public ProjectIntegrationTarget save(ProjectIntegrationTarget target) {
        return jpa.save(target);
    }

    @Override
    public Optional<ProjectIntegrationTarget> findByProjectId(UUID projectId) {
        return jpa.findByProjectId(projectId);
    }

    @Override
    public void delete(ProjectIntegrationTarget target) {
        jpa.delete(target);
    }
}
