package com.kntro.reqsai.billing.application.metering;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.shared.application.ai.AiUsageReporter;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * {@link AiUsageReporter} that writes the ledger and the counter off the calling thread. The AI call
 * that was just paid for is never delayed by, nor failed by, the database write behind it, and the write
 * does not hold a second connection while the caller's transaction holds the first. Two worker threads
 * keep the pressure on the connection pool small; each report runs under the tenant captured when it was
 * made. Every failure is logged and dropped.
 */
@Component
@Slf4j
class BillingAiUsageReporter implements AiUsageReporter {

    private final BillingModuleApi billing;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ai-usage-reporter");
        thread.setDaemon(true);
        return thread;
    });

    BillingAiUsageReporter(BillingModuleApi billing) {
        this.billing = billing;
    }

    @Override
    public void report(TokenUsage usage) {
        try {
            String tenant = TenantContext.getCurrentTenant();
            if (tenant == null || tenant.isBlank() || TenantContext.DEFAULT_SCHEMA.equals(tenant)) {
                log.debug("No tenant bound; skipping the ledger entry of {} tokens", usage.totalTokens());
                return;
            }
            UUID organizationId = UUID.fromString(tenant);
            TokenUsage resolved = usage.within(AiUsageScope.current());
            TenantContext.TenantSnapshot snapshot = TenantContext.capture();
            executor.execute(() -> TenantContext.runWith(snapshot, () -> record(organizationId, resolved)));
        } catch (RuntimeException e) {
            log.warn("AI usage could not be queued ({} tokens): {}", usage.totalTokens(), e.getMessage());
        }
    }

    private void record(UUID organizationId, TokenUsage usage) {
        try {
            billing.recordAiUsage(organizationId, usage);
        } catch (RuntimeException e) {
            log.warn("AI usage of org {} was not recorded ({} {} tokens): {}", organizationId, usage.feature(),
                    usage.totalTokens(), e.getMessage());
        }
    }

    @PreDestroy
    void stop() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
