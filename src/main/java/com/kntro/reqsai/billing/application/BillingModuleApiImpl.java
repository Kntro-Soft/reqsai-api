package com.kntro.reqsai.billing.application;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.billing.api.PlanLimitsSnapshot;
import com.kntro.reqsai.billing.application.command.AssignFreeSubscriptionCommand;
import com.kntro.reqsai.billing.application.command.RecordTokenConsumptionCommand;
import com.kntro.reqsai.billing.application.handler.AssignFreeSubscriptionCommandHandler;
import com.kntro.reqsai.billing.application.handler.RecordTokenConsumptionCommandHandler;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.PlanCatalog;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanLimitsValues;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Package-private implementation of the {@link BillingModuleApi} port.
 */
@Component
@RequiredArgsConstructor
class BillingModuleApiImpl implements BillingModuleApi {

    private final AssignFreeSubscriptionCommandHandler assignFreeSubscriptionHandler;
    private final RecordTokenConsumptionCommandHandler recordTokenConsumptionHandler;
    private final SubscriptionRepositoryPort subscriptions;

    @Override
    public PlanLimitsSnapshot freePlanLimits() {
        return toSnapshot(PlanCatalog.limitsFor(PlanType.FREE));
    }

    @Override
    public PlanLimitsSnapshot planLimits(String planType) {
        return toSnapshot(PlanCatalog.limitsFor(PlanType.valueOf(planType)));
    }

    @Override
    public void assignFreeSubscription(UUID organizationId) {
        assignFreeSubscriptionHandler.handle(new AssignFreeSubscriptionCommand(organizationId));
    }

    @Override
    public void recordTokenConsumption(UUID organizationId, long tokens) {
        recordTokenConsumptionHandler.handle(new RecordTokenConsumptionCommand(organizationId, tokens));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasTokenQuotaAvailable(UUID organizationId) {
        return subscriptions.findByOrganizationId(organizationId)
                .map(this::withinQuota)
                .orElse(true); // fail open for a mis-provisioned organization
    }

    private boolean withinQuota(Subscription subscription) {
        long limit = PlanCatalog.limitsFor(subscription.getPlanType()).maxTokensPerMonth();
        return subscription.getTokenQuotaUsed() < limit;
    }

    private static PlanLimitsSnapshot toSnapshot(PlanLimitsValues values) {
        return new PlanLimitsSnapshot(
                values.maxMembers(),
                values.maxProjects(),
                values.maxDocumentsPerProject(),
                values.maxTokensPerMonth(),
                values.maxGlossaryTermsPerProject()
        );
    }
}
