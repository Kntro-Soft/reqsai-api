package com.kntro.reqsai.billing.infrastructure.payment.stripe;

import com.kntro.reqsai.billing.application.port.ProcessedEventStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA-backed {@link ProcessedEventStorePort}. A pre-check on the provider event id (its primary key)
 * makes redelivered webhooks a no-op without tainting the surrounding transaction with a constraint
 * violation.
 */
@Component
@RequiredArgsConstructor
class ProcessedStripeEventStore implements ProcessedEventStorePort {

    private final ProcessedStripeEventJpaRepository repository;

    @Override
    public boolean markProcessed(String eventId, String eventType) {
        if (repository.existsById(eventId)) {
            return false;
        }
        repository.save(new ProcessedStripeEvent(eventId, eventType));
        return true;
    }
}
