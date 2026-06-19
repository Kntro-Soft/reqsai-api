package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectJpaRepository;
import lombok.RequiredArgsConstructor;
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
    public boolean existsByName(String name) {
        return jpa.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, UUID id) {
        return jpa.existsByNameAndIdNot(name, id);
    }

    @Override
    public int countActive() {
        return jpa.countActive();
    }

    @Override
    public void delete(Project project) {
        jpa.delete(project);
    }
}
