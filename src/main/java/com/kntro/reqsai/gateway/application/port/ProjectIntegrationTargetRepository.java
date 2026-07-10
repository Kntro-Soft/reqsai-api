package com.kntro.reqsai.gateway.application.port;

import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for the {@link ProjectIntegrationTarget} aggregate. Tenant-scoped. */
public interface ProjectIntegrationTargetRepository {

    ProjectIntegrationTarget save(ProjectIntegrationTarget target);

    Optional<ProjectIntegrationTarget> findByProjectId(UUID projectId);

    void delete(ProjectIntegrationTarget target);
}
