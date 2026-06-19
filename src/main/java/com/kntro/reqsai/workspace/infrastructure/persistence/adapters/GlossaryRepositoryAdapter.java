package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.GlossaryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GlossaryRepositoryAdapter implements GlossaryRepository {

    private final GlossaryJpaRepository jpa;

    @Override
    public Glossary save(Glossary glossary) {
        return jpa.save(glossary);
    }
}
