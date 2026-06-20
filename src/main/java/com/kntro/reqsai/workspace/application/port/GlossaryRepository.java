package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Glossary;

import java.util.Optional;
import java.util.UUID;

public interface GlossaryRepository {
    Glossary save(Glossary glossary);
    Optional<Glossary> findByProjectId(UUID projectId);
}
