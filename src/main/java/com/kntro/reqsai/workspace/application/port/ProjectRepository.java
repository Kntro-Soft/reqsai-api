package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    Project save(Project project);
    Optional<Project> findById(UUID id);
    List<Project> findAllByOrganizationId(UUID organizationId);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
    int countActive();
    void delete(Project project);
}
