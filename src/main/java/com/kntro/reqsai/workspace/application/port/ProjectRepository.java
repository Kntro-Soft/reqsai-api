package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    Project save(Project project);
    Optional<Project> findById(UUID id);
    Page<Project> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
    boolean existsByOrganizationIdAndNameAndIdNot(UUID organizationId, String name, UUID id);
    int countActiveByOrganizationId(UUID organizationId);
    void delete(Project project);
}
