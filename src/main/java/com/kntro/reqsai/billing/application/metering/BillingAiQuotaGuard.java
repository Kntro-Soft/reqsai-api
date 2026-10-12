package com.kntro.reqsai.billing.application.metering;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.billing.api.TokenQuotaStatus;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link AiQuotaGuard} backed by the organization's subscription. The organization is the tenant bound to
 * the calling thread (the JWT {@code orgId}). Fails open on anything but a spent allowance: no tenant
 * bound, no subscription, or a database error all let the AI call through.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class BillingAiQuotaGuard implements AiQuotaGuard {

    private final BillingModuleApi billing;

    @Override
    public boolean isAvailable() {
        TokenQuotaStatus status = status();
        return status == null || status.available();
    }

    @Override
    public void assertAvailable() {
        TokenQuotaStatus status = status();
        if (status != null && !status.available()) {
            throw BillingExceptions.aiQuotaExceeded(status.planType(), status.limit(), status.used(),
                    status.resetsAt());
        }
    }

    /** The organization's allowance, or {@code null} when it cannot be read (fail open). */
    private TokenQuotaStatus status() {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.isBlank() || TenantContext.DEFAULT_SCHEMA.equals(tenant)) {
            return null;
        }
        try {
            return billing.tokenQuotaStatus(UUID.fromString(tenant));
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI quota check failed for tenant {}; allowing the call: {}", tenant, e.getMessage());
            return null;
        }
    }
}
