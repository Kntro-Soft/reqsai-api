package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    Project save(Project project);
    Optional<Project> findById(UUID id);
    Optional<Project> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Project> findByIdAndOrganizationIdAndStatus(UUID id, UUID organizationId, ProjectStatus status);
    Page<Project> findAllByOrganizationIdAndStatus(UUID organizationId, ProjectStatus status, Pageable pageable);
    Page<Project> findAllByOrganizationIdAndStatusAndIdIn(UUID organizationId, ProjectStatus status, Collection<UUID> ids, Pageable pageable);
    boolean existsByOrganizationIdAndNameAndStatus(UUID organizationId, String name, ProjectStatus status);
    boolean existsByOrganizationIdAndNameAndIdNotAndStatus(UUID organizationId, String name, UUID id, ProjectStatus status);
    int countActiveByOrganizationId(UUID organizationId);
    void delete(Project project);
}
