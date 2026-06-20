package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
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
    public Page<Project> findAllByOrganizationId(UUID organizationId, Pageable pageable) {
        return jpa.findAllByOrganizationId(organizationId, pageable);
    }

    @Override
    public boolean existsByOrganizationIdAndName(UUID organizationId, String name) {
        return jpa.existsByOrganizationIdAndName(organizationId, name);
    }

    @Override
    public boolean existsByOrganizationIdAndNameAndIdNot(UUID organizationId, String name, UUID id) {
        return jpa.existsByOrganizationIdAndNameAndIdNot(organizationId, name, id);
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
