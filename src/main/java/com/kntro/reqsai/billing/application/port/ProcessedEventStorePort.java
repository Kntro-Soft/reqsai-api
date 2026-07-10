package com.kntro.reqsai.billing.application.port;

/**
 * Idempotency store for payment-provider webhook events: records which provider event ids have
 * already been handled so a redelivery is a no-op.
 */
public interface ProcessedEventStorePort {

    /**
     * Atomically records the event id as processed.
     *
     * @param eventId provider event id
     * @param eventType provider event type (stored for diagnostics)
     * @return {@code true} if this call recorded the id (first time), {@code false} if it was already present
     */
    boolean markProcessed(String eventId, String eventType);
}
