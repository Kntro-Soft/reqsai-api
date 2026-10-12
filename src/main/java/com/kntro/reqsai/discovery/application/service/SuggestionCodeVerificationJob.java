package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.CodeVerification;
import com.kntro.reqsai.discovery.application.port.CodeVerificationPort.SubjectKind;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Runs the deep verification of a suggestion in the background, bound to the caller's tenant, after the request
 * that asked for it has committed. It never throws: whatever goes wrong (code host down, model down, timeout)
 * ends as the suggestion's {@code FAILED} state, which the card shows with a retry.
 */
@Component
@Slf4j
public class SuggestionCodeVerificationJob {

    private final CodeVerificationService verification;
    private final SuggestionCodeVerificationWriter writer;
    private final TaskExecutor executor;

    public SuggestionCodeVerificationJob(CodeVerificationService verification,
                                         SuggestionCodeVerificationWriter writer,
                                         @Qualifier("taskExecutor") TaskExecutor executor) {
        this.verification = verification;
        this.writer = writer;
        this.executor = executor;
    }

    /** Starts the run once the current transaction commits (right away when there is none). */
    public void launch(UUID projectId, UUID suggestionId) {
        TenantContext.TenantSnapshot tenant = TenantContext.capture();
        Runnable start = () -> executor.execute(() -> TenantContext.runWith(tenant, () -> run(projectId, suggestionId)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    start.run();
                }
            });
        } else {
            start.run();
        }
    }

    void run(UUID projectId, UUID suggestionId) {
        try {
            var target = writer.load(projectId, suggestionId);
            if (target.isEmpty()) {
                log.debug("Deep verification of suggestion {} skipped: no longer waiting", suggestionId);
                return;
            }
            CodeVerification result = verification.verify(projectId, target.get().subject(),
                    SubjectKind.REQUIREMENT, target.get().language(), verification.budget());
            writer.complete(projectId, suggestionId, result);
        } catch (RuntimeException e) {
            log.warn("Deep verification of suggestion {} failed: {}", suggestionId, e.getMessage());
            try {
                writer.fail(projectId, suggestionId, e.getMessage());
            } catch (RuntimeException inner) {
                log.error("Could not record the failed verification of suggestion {}", suggestionId, inner);
            }
        }
    }
}
