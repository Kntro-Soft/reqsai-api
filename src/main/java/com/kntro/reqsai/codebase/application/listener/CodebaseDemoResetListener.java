package com.kntro.reqsai.codebase.application.listener;

import com.kntro.reqsai.codebase.application.port.CodeModuleRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.workspace.api.DemoProjectSeededIntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Resets the demo project's connected code whenever Workspace (re)seeds it (US28): every repository and its
 * module map are removed, so the demo starts without code like a new project. The organization's GitHub
 * connection is not the project's and stays; an indexing run still in flight finds its repository gone and
 * stops writing.
 * <p>
 * A plain synchronous {@link EventListener}, like Discovery's: it runs inside the seeding transaction with the
 * tenant bound, so the restore commits or rolls back as one unit.
 */
@Component
@RequiredArgsConstructor
class CodebaseDemoResetListener {

    private final CodeRepositoryRepository repositories;
    private final CodeModuleRepository modules;

    @EventListener
    void onDemoProjectSeeded(DemoProjectSeededIntegrationEvent event) {
        repositories.findAllByProjectId(event.projectId()).forEach(repository -> {
            modules.deleteByRepositoryId(repository.getId());
            repositories.delete(repository);
        });
    }
}
