package com.kntro.reqsai.billing.infrastructure.payment.stripe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Idempotency record for a processed payment-provider webhook event. Technical concern (not a domain
 * aggregate): the primary key is the provider's own event id (a String), so it does not extend the
 * UUID-based {@code AuditableEntity}.
 */
@Entity
@Table(name = "billing_processed_events", schema = "public")
class ProcessedStripeEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedStripeEvent() {
        // Required by JPA
    }

    ProcessedStripeEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    String getEventId() {
        return eventId;
    }
}
