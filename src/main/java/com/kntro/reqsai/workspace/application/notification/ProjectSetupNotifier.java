package com.kntro.reqsai.workspace.application.notification;

import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.application.service.ProjectSetupViews;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectSetupResponseMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pushes a project's whole setup to its topic whenever something about it changed. Pushes of a project are spaced
 * out ({@link #MIN_GAP_MS}): a change that lands inside the gap is sent once the gap has passed, so the last state
 * always goes out while indexing, which reports every module, does not flood the socket. A change inside a
 * transaction is sent after it commits. The durable state stays the source of truth: the page also polls.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectSetupNotifier {

    static final long MIN_GAP_MS = 1000;

    private final ProjectSetupViews views;
    private final RealtimeNotifier notifier;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "project-setup-notifier");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, Long> lastPush = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> queued = new ConcurrentHashMap<>();

    /** Pushes the project's setup soon, once its own transaction (if any) has committed. */
    public void changed(UUID projectId) {
        TenantContext.TenantSnapshot tenant = TenantContext.capture();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(projectId, tenant);
                }
            });
        } else {
            schedule(projectId, tenant);
        }
    }

    private void schedule(UUID projectId, TenantContext.TenantSnapshot tenant) {
        if (queued.putIfAbsent(projectId, Boolean.TRUE) != null) return;
        long wait = Math.max(0, lastPush.getOrDefault(projectId, 0L) + MIN_GAP_MS - System.currentTimeMillis());
        scheduler.schedule(() -> {
            queued.remove(projectId);
            lastPush.put(projectId, System.currentTimeMillis());
            TenantContext.runWith(tenant, () -> push(projectId));
        }, wait, TimeUnit.MILLISECONDS);
    }

    private void push(UUID projectId) {
        try {
            var setup = views.of(projectId);
            if (setup.steps().isEmpty()) return;
            notifier.broadcast(ProjectSetupTopics.of(projectId), ProjectSetupResponseMapper.toResponse(setup));
        } catch (RuntimeException e) {
            log.warn("Could not push the setup of project {}: {}", projectId, e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
