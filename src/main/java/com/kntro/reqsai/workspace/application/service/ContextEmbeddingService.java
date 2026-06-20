package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.event.GlossaryTermSavedEvent;
import com.kntro.reqsai.workspace.domain.event.ProjectConstraintSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Computes and persists pgvector embeddings for glossary terms and project constraints
 * whenever they are added or updated. Runs in a separate transaction after the aggregate
 * is committed so the embedding never blocks the write path.
 * No-op when the embedding model is not configured ({@code ai.model.embedding=none}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ContextEmbeddingService {

    private final EmbeddingPort embeddingPort;
    private final GlossaryRepository glossaries;
    private final ProjectRepository projects;

    @ApplicationModuleListener
    void onTermSaved(GlossaryTermSavedEvent event) {
        if (!embeddingPort.isAvailable()) return;
        try {
            float[] vector = embeddingPort.embed(event.term() + ": " + event.definition());
            glossaries.findByProjectId(event.projectId()).ifPresent(glossary -> {
                glossary.applyTermEmbedding(event.termId(), vector);
                glossaries.save(glossary);
            });
        } catch (Exception e) {
            log.warn("Failed to embed glossary term {} for project {}: {}", event.termId(), event.projectId(), e.getMessage());
        }
    }

    @ApplicationModuleListener
    void onConstraintSaved(ProjectConstraintSavedEvent event) {
        if (!embeddingPort.isAvailable()) return;
        try {
            float[] vector = embeddingPort.embed(event.description());
            projects.findById(event.projectId()).ifPresent(project -> {
                project.applyConstraintEmbedding(event.constraintId(), vector);
                projects.save(project);
            });
        } catch (Exception e) {
            log.warn("Failed to embed constraint {} for project {}: {}", event.constraintId(), event.projectId(), e.getMessage());
        }
    }
}
