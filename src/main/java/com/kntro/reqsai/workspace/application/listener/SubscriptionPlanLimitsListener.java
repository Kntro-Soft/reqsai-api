package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.billing.api.PlanLimitsSnapshot;
import com.kntro.reqsai.billing.api.SubscriptionPlanChangedIntegrationEvent;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps an Organization's operational limits in sync with its Billing plan. When Billing announces a
 * plan change ({@link SubscriptionPlanChangedIntegrationEvent} on {@code billing::api}), this loads the
 * new tier's limits from Billing (the single source of truth for the numbers) and applies them to the
 * Organization aggregate. Organizations live in {@code public}, so no tenant context is needed; runs
 * after commit in its own transaction (Spring Modulith).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SubscriptionPlanLimitsListener {

    private final OrganizationRepository organizations;
    private final BillingModuleApi billing;

    @ApplicationModuleListener
    void onPlanChanged(SubscriptionPlanChangedIntegrationEvent event) {
        organizations.findById(event.organizationId()).ifPresentOrElse(
                organization -> applyLimits(organization, event.planType()),
                () -> log.warn("Plan changed to {} for unknown org {}; limits not updated",
                        event.planType(), event.organizationId())
        );
    }

    private void applyLimits(Organization organization, String planType) {
        PlanLimitsSnapshot limits = billing.planLimits(planType);
        organization.updateLimits(new PlanLimits(
                limits.maxMembers(),
                limits.maxProjects(),
                limits.maxDocumentsPerProject(),
                limits.maxTokensPerMonth(),
                limits.maxGlossaryTermsPerProject()
        ));
        organizations.save(organization);
        log.info("Organization {} limits updated to plan {}", organization.getId(), planType);
    }
}
