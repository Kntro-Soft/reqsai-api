package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectConstraintRepository;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectConstraintSearchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Adapts the {@link ProjectConstraintRepository} read port to Spring Data JPA. */
@Component
@RequiredArgsConstructor
public class ProjectConstraintRepositoryAdapter implements ProjectConstraintRepository {

    private final ProjectConstraintSearchJpaRepository jpa;

    @Override
    public Page<ProjectConstraint> findByProjectId(UUID projectId, @Nullable String search, Pageable pageable) {
        String normalized = (search == null || search.isBlank()) ? null : search.strip();
        return jpa.findPageByProjectId(projectId, normalized, pageable);
    }
}
