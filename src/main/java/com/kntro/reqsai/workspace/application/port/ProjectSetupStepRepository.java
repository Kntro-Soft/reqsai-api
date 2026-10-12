package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.ProjectSetupStep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for the steps of a project's setup. Tenant-scoped. */
public interface ProjectSetupStepRepository {

    ProjectSetupStep save(ProjectSetupStep step);

    List<ProjectSetupStep> findAllByProjectId(UUID projectId);

    Optional<ProjectSetupStep> findByProjectIdAndStepKey(UUID projectId, String stepKey);
}
