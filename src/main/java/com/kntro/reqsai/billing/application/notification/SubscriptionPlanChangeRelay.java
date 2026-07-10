package com.kntro.reqsai.billing.application.notification;

import com.kntro.reqsai.billing.api.SubscriptionPlanChangedIntegrationEvent;
import com.kntro.reqsai.billing.domain.event.SubscriptionDowngradedEvent;
import com.kntro.reqsai.billing.domain.event.SubscriptionUpgradedEvent;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Relays Billing's internal plan-change domain events to the public
 * {@link SubscriptionPlanChangedIntegrationEvent} on {@code billing::api}, so other modules can react
 * to a plan change without importing Billing's domain events. Keeps the domain layer free of
 * cross-module wiring (mirrors IAM's {@code AccountVerifiedEventRelay}).
 */
@Component
@RequiredArgsConstructor
class SubscriptionPlanChangeRelay {

    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void onUpgraded(SubscriptionUpgradedEvent event) {
        events.publishEvent(SubscriptionPlanChangedIntegrationEvent.of(event.organizationId(), event.newPlanType()));
    }

    @ApplicationModuleListener
    void onDowngraded(SubscriptionDowngradedEvent event) {
        events.publishEvent(SubscriptionPlanChangedIntegrationEvent.of(event.organizationId(), PlanType.FREE.name()));
    }
}
