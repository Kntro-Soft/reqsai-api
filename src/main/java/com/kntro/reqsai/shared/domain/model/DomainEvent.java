package com.kntro.reqsai.shared.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for Domain Events (DDD).
 * <p>
 * Domain events describe something meaningful that already happened in the domain. They are
 * immutable (prefer {@code record}s), named in the past tense (e.g. {@code OrganizationRegistered}),
 * and carry the minimum data consumers need.
 * <p>
 * Register them from an aggregate via {@link AggregateRoot#registerEvent(Object)}; Spring Data
 * publishes them on save and other modules consume them with
 * {@code @org.springframework.modulith.events.ApplicationModuleListener}.
 *
 * @see AggregateRoot#registerEvent(Object)
 */
public interface DomainEvent extends Serializable {

    /**
     * @return id of the aggregate that produced this event
     */
    UUID aggregateId();

    /**
     * @return instant the event occurred
     */
    Instant occurredAt();
}
