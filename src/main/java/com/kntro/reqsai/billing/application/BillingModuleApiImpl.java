package com.kntro.reqsai.billing.application;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.billing.api.PlanLimitsSnapshot;
import com.kntro.reqsai.billing.application.command.AssignFreeSubscriptionCommand;
import com.kntro.reqsai.billing.application.handler.AssignFreeSubscriptionCommandHandler;
import com.kntro.reqsai.billing.domain.model.PlanCatalog;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanLimitsValues;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Package-private implementation of the {@link BillingModuleApi} port.
 */
@Component
@RequiredArgsConstructor
class BillingModuleApiImpl implements BillingModuleApi {

    private final AssignFreeSubscriptionCommandHandler assignFreeSubscriptionHandler;

    @Override
    public PlanLimitsSnapshot freePlanLimits() {
        PlanLimitsValues values = PlanCatalog.limitsFor(PlanType.FREE);
        return new PlanLimitsSnapshot(
                values.maxMembers(),
                values.maxProjects(),
                values.maxDocumentsPerProject(),
                values.maxTokensPerMonth(),
                values.maxGlossaryTermsPerProject()
        );
    }

    @Override
    public void assignFreeSubscription(UUID organizationId) {
        assignFreeSubscriptionHandler.handle(new AssignFreeSubscriptionCommand(organizationId));
    }
}
