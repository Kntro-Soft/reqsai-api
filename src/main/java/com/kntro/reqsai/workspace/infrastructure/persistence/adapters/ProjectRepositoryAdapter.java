package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final ProjectJpaRepository jpa;

    @Override
    public Project save(Project project) {
        return jpa.save(project);
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Project> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return jpa.findByIdAndOrganizationId(id, organizationId);
    }

    @Override
    public Optional<Project> findByIdAndOrganizationIdAndStatus(UUID id, UUID organizationId, ProjectStatus status) {
        return jpa.findByIdAndOrganizationIdAndStatus(id, organizationId, status);
    }

    @Override
    public Page<Project> findAllByOrganizationIdAndStatus(UUID organizationId, ProjectStatus status, Pageable pageable) {
        return jpa.findAllByOrganizationIdAndStatus(organizationId, status, pageable);
    }

    @Override
    public boolean existsByOrganizationIdAndNameAndStatus(UUID organizationId, String name, ProjectStatus status) {
        return jpa.existsByOrganizationIdAndNameAndStatus(organizationId, name, status);
    }

    @Override
    public boolean existsByOrganizationIdAndNameAndIdNotAndStatus(UUID organizationId, String name, UUID id, ProjectStatus status) {
        return jpa.existsByOrganizationIdAndNameAndIdNotAndStatus(organizationId, name, id, status);
    }

    @Override
    public int countActiveByOrganizationId(UUID organizationId) {
        return jpa.countActiveByOrganizationId(organizationId);
    }

    @Override
    public void delete(Project project) {
        jpa.delete(project);
    }
}
