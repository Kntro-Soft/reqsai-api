package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.ProjectRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRoleRepository {
    ProjectRole save(ProjectRole role);
    Optional<ProjectRole> findByIdAndProjectId(UUID id, UUID projectId);
    List<ProjectRole> findAllByProjectId(UUID projectId);
    boolean existsByProjectIdAndName(UUID projectId, String name);
    boolean existsByProjectIdAndNameAndIdNot(UUID projectId, String name, UUID id);
    void delete(ProjectRole role);
}
