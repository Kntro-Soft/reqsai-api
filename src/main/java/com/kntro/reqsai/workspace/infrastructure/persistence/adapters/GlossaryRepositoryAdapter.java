package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.GlossaryJpaRepository;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.GlossaryTermSearchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GlossaryRepositoryAdapter implements GlossaryRepository {

    private final GlossaryJpaRepository jpa;
    private final GlossaryTermSearchJpaRepository termJpa;

    @Override
    public Glossary save(Glossary glossary) {
        return jpa.save(glossary);
    }

    @Override
    public Optional<Glossary> findByProjectId(UUID projectId) {
        return jpa.findByProjectId(projectId);
    }

    @Override
    public Page<GlossaryTerm> findTermsByProjectId(UUID projectId, @Nullable String search, Pageable pageable) {
        String normalized = (search == null || search.isBlank()) ? null : search.strip();
        return termJpa.findPageByProjectId(projectId, normalized, pageable);
    }
}
