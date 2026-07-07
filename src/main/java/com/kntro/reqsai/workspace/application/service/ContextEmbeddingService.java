package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.application.listener.TenantAwareModuleListener;
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
 *
 * <p>DB writes are delegated to {@link ContextEmbeddingWriteService} so
 * {@code @Transactional(REQUIRES_NEW)} is invoked through the Spring proxy (not a self-call),
 * ensuring the correct tenant schema is in effect when Hibernate opens the connection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ContextEmbeddingService extends TenantAwareModuleListener {

    private final EmbeddingPort embeddingPort;
    private final ContextEmbeddingWriteService writer;

    @ApplicationModuleListener
    void onTermSaved(GlossaryTermSavedEvent event) {
        if (!embeddingPort.isAvailable()) return;
        withTenant(event, () -> writer.embedTerm(event));
    }

    @ApplicationModuleListener
    void onConstraintSaved(ProjectConstraintSavedEvent event) {
        if (!embeddingPort.isAvailable()) return;
        withTenant(event, () -> writer.embedConstraint(event));
    }
}
