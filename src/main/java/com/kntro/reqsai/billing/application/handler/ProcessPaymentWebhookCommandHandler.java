package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.port.PaymentWebhookEvent;
import com.kntro.reqsai.billing.application.port.PaymentWebhookParserPort;
import com.kntro.reqsai.billing.application.port.ProcessedEventStorePort;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProvider;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Verifies, de-duplicates and applies a payment-provider webhook.
 * <p>
 * Signature verification and parsing are delegated to {@link PaymentWebhookParserPort}; idempotency is
 * enforced via {@link ProcessedEventStorePort} (a redelivered event is a no-op). Applying the effect
 * to the {@link Subscription} aggregate is where the plan actually changes for the real gateway, so
 * the same lifecycle methods used by the fake path keep the domain identical across gateways.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProcessPaymentWebhookCommandHandler {

    private final PaymentWebhookParserPort parser;
    private final ProcessedEventStorePort processedEvents;
    private final SubscriptionRepositoryPort subscriptions;

    public void handle(String payload, String signatureHeader) {
        PaymentWebhookEvent event = parser.verifyAndParse(payload, signatureHeader);

        if (event.kind() == PaymentWebhookEvent.Kind.IGNORED) {
            log.debug("Ignoring webhook event {} (not actionable)", event.eventId());
            return;
        }
        if (!processedEvents.markProcessed(event.eventId(), event.kind().name())) {
            log.info("Webhook event {} already processed — skipping", event.eventId());
            return;
        }

        switch (event.kind()) {
            case PLAN_ACTIVATED -> activatePlan(event);
            case SUBSCRIPTION_DELETED -> downgrade(event);
            case PAYMENT_FAILED -> markPastDue(event);
            default -> { /* IGNORED handled above */ }
        }
    }

    private void activatePlan(PaymentWebhookEvent event) {
        if (event.organizationId() == null || event.targetPlan() == null) {
            log.warn("PLAN_ACTIVATED webhook {} missing organizationId/targetPlan; skipping", event.eventId());
            return;
        }
        subscriptions.findByOrganizationId(event.organizationId()).ifPresentOrElse(
                subscription -> {
                    subscription.upgradeTo(event.targetPlan(),
                            PaymentProviderRef.of(PaymentProvider.STRIPE, event.externalSubscriptionId()));
                    subscriptions.save(subscription);
                    log.info("Webhook {} activated plan {} for org {}", event.eventId(),
                            event.targetPlan(), event.organizationId());
                },
                () -> log.warn("PLAN_ACTIVATED webhook {} for unknown org {}", event.eventId(), event.organizationId())
        );
    }

    private void downgrade(PaymentWebhookEvent event) {
        resolveBySubscriptionId(event).ifPresent(subscription -> {
            subscription.downgradeToFree();
            subscriptions.save(subscription);
            log.info("Webhook {} downgraded subscription {} to FREE", event.eventId(), subscription.getId());
        });
    }

    private void markPastDue(PaymentWebhookEvent event) {
        resolveBySubscriptionId(event).ifPresent(subscription -> {
            subscription.markPastDue();
            subscriptions.save(subscription);
            log.info("Webhook {} flagged subscription {} past-due", event.eventId(), subscription.getId());
        });
    }

    private Optional<Subscription> resolveBySubscriptionId(PaymentWebhookEvent event) {
        if (event.externalSubscriptionId() == null) {
            log.warn("Webhook {} ({}) missing external subscription id; skipping", event.eventId(), event.kind());
            return Optional.empty();
        }
        Optional<Subscription> found = subscriptions.findByProviderExternalId(event.externalSubscriptionId());
        if (found.isEmpty()) {
            log.warn("Webhook {} references unknown external subscription {}", event.eventId(), event.externalSubscriptionId());
        }
        return found;
    }
}
