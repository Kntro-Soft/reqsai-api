package com.kntro.reqsai.billing.application;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.billing.api.PlanLimitsSnapshot;
import com.kntro.reqsai.billing.api.TokenQuotaStatus;
import com.kntro.reqsai.billing.application.command.AssignFreeSubscriptionCommand;
import com.kntro.reqsai.billing.application.command.RecordAiUsageCommand;
import com.kntro.reqsai.billing.application.command.RecordTokenConsumptionCommand;
import com.kntro.reqsai.billing.application.handler.AssignFreeSubscriptionCommandHandler;
import com.kntro.reqsai.billing.application.handler.RecordAiUsageCommandHandler;
import com.kntro.reqsai.billing.application.handler.RecordTokenConsumptionCommandHandler;
import com.kntro.reqsai.billing.application.notification.QuotaWarningNotifier;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.PlanCatalog;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanLimitsValues;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Package-private implementation of the {@link BillingModuleApi} port.
 */
@Component
@RequiredArgsConstructor
class BillingModuleApiImpl implements BillingModuleApi {

    private final AssignFreeSubscriptionCommandHandler assignFreeSubscriptionHandler;
    private final RecordTokenConsumptionCommandHandler recordTokenConsumptionHandler;
    private final RecordAiUsageCommandHandler recordAiUsageHandler;
    private final QuotaWarningNotifier warnings;
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
    public void recordAiUsage(UUID organizationId, TokenUsage usage) {
        RecordAiUsageCommandHandler.Outcome outcome =
                recordAiUsageHandler.handle(new RecordAiUsageCommand(organizationId, usage));
        if (outcome.newWarning() != null && outcome.counter().isPresent()) {
            warnings.warn(organizationId, outcome.newWarning(), outcome.counter().get().used(), outcome.limit());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasTokenQuotaAvailable(UUID organizationId) {
        return tokenQuotaStatus(organizationId).available();
    }

    @Override
    @Transactional(readOnly = true)
    public TokenQuotaStatus tokenQuotaStatus(UUID organizationId) {
        return subscriptions.findByOrganizationId(organizationId)
                .map(this::statusOf)
                .orElseGet(TokenQuotaStatus::unlimited); // fail open for a mis-provisioned organization
    }

    private TokenQuotaStatus statusOf(Subscription subscription) {
        long limit = PlanCatalog.limitsFor(subscription.getPlanType()).maxTokensPerMonth();
        long used = subscription.effectiveTokensUsed(Instant.now());
        return new TokenQuotaStatus(subscription.getPlanType().name(), limit, used,
                subscription.getCurrentPeriodEnd(), used < limit);
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
