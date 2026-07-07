package com.kntro.reqsai.billing.infrastructure.payment;

import com.kntro.reqsai.billing.application.port.PaymentGatewayPort;
import com.kntro.reqsai.billing.application.port.PlanChangeRequest;
import com.kntro.reqsai.billing.application.port.PlanChangeResult;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProvider;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link PaymentGatewayPort} for local development and CI: activates the target plan
 * immediately without contacting any external provider or charging money. Active unless
 * {@code reqsai.billing.payment-provider=stripe} (the {@code matchIfMissing} default).
 * <p>
 * Swapping to the real Stripe gateway is a config-only change — the domain and command handlers are
 * unaffected because both adapters honour the {@link PlanChangeResult} contract.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "reqsai.billing.payment-provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public String providerName() {
        return "FAKE";
    }

    @Override
    public PlanChangeResult startPlanChange(PlanChangeRequest request) {
        String externalId = "fake_sub_" + UUID.randomUUID();
        log.info("[FAKE gateway] Activating plan {} for org {} immediately (external id {})",
                request.targetPlan(), request.organizationId(), externalId);
        return PlanChangeResult.activated(PaymentProviderRef.of(PaymentProvider.STRIPE, externalId));
    }

    @Override
    public void cancelExternalSubscription(PaymentProviderRef providerRef) {
        log.info("[FAKE gateway] Cancel external subscription {} (no-op)",
                providerRef != null ? providerRef.externalId() : null);
    }
}
