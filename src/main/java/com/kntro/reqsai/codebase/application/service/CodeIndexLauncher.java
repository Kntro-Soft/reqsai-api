package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Starts an indexing run in the background, bound to the caller's tenant. Inside a transaction the run
 * starts after the commit, so it always reads the repository the caller just saved.
 */
@Component
public class CodeIndexLauncher {

    private final CodeIndexer indexer;
    private final TaskExecutor executor;

    public CodeIndexLauncher(CodeIndexer indexer, @Qualifier("taskExecutor") TaskExecutor executor) {
        this.indexer = indexer;
        this.executor = executor;
    }

    public void launch(UUID repositoryId) {
        TenantContext.TenantSnapshot tenant = TenantContext.capture();
        Runnable run = () -> executor.execute(() -> TenantContext.runWith(tenant, () -> indexer.index(repositoryId)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    run.run();
                }
            });
        } else {
            run.run();
        }
    }
}
