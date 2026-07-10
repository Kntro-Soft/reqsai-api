package com.kntro.reqsai.discovery.infrastructure.billing;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.discovery.application.port.TokenUsageRecorderPort;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link TokenUsageRecorderPort} adapter that meters AI token consumption against the current tenant's
 * subscription via {@code billing::api}. The tenant id bound on the request thread is the JWT
 * {@code orgId} (a plain UUID), which is exactly the organization the subscription is keyed by.
 * <p>
 * Best-effort by contract: any failure (no tenant bound, billing error) is swallowed and logged so
 * metering never interferes with requirement generation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillingTokenUsageRecorder implements TokenUsageRecorderPort {

    private final BillingModuleApi billing;

    @Override
    public void record(long totalTokens) {
        if (totalTokens <= 0) {
            return;
        }
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.isBlank() || TenantContext.DEFAULT_SCHEMA.equals(tenant)) {
            log.debug("No tenant bound; skipping token metering of {} tokens", totalTokens);
            return;
        }
        try {
            billing.recordTokenConsumption(UUID.fromString(tenant), totalTokens);
        } catch (RuntimeException e) {
            log.warn("Token metering failed for tenant {} ({} tokens): {}", tenant, totalTokens, e.getMessage());
        }
    }
}
