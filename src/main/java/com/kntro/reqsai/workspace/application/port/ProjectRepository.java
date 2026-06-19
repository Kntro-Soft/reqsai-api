package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Project;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    Project save(Project project);
    Optional<Project> findById(UUID id);
    boolean existsByName(String name);
    int countActive();
}
