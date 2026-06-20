package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Glossary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GlossaryJpaRepository extends JpaRepository<Glossary, UUID> {
    Optional<Glossary> findByProjectId(UUID projectId);
}
