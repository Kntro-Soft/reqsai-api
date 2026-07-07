package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectRoleRepositoryAdapter implements ProjectRoleRepository {

    private final ProjectRoleJpaRepository jpa;

    @Override
    public ProjectRole save(ProjectRole role) {
        return jpa.save(role);
    }

    @Override
    public Optional<ProjectRole> findByIdAndProjectId(UUID id, UUID projectId) {
        return jpa.findByIdAndProjectId(id, projectId);
    }

    @Override
    public List<ProjectRole> findAllByProjectId(UUID projectId) {
        return jpa.findAllByProjectId(projectId);
    }

    @Override
    public boolean existsByProjectIdAndName(UUID projectId, String name) {
        return jpa.existsByProjectIdAndName(projectId, name);
    }

    @Override
    public boolean existsByProjectIdAndNameAndIdNot(UUID projectId, String name, UUID id) {
        return jpa.existsByProjectIdAndNameAndIdNot(projectId, name, id);
    }

    @Override
    public void delete(ProjectRole role) {
        jpa.delete(role);
    }
}
