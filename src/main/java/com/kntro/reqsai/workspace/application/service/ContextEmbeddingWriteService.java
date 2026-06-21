package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.event.GlossaryTermSavedEvent;
import com.kntro.reqsai.workspace.domain.event.ProjectConstraintSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes embedding writes in a fresh transaction so the correct tenant schema
 * is resolved from the TenantContext set by ContextEmbeddingService.withTenant().
 * Must be a separate Spring bean — @Transactional(REQUIRES_NEW) is bypassed on
 * self-calls inside the same class.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ContextEmbeddingWriteService {

    private final EmbeddingPort embeddingPort;
    private final GlossaryRepository glossaries;
    private final ProjectRepository projects;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void embedTerm(GlossaryTermSavedEvent event) {
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void embedConstraint(ProjectConstraintSavedEvent event) {
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
