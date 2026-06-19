package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<Project, UUID> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.status = 'ACTIVE'")
    int countActive();
}
