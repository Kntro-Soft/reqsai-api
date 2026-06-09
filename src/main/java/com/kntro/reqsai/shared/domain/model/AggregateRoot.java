package com.kntro.reqsai.shared.domain.model;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for <strong>aggregate roots</strong> — the entry point and consistency boundary of an
 * aggregate (what repositories load/save).
 * <p>
 * Extends {@link AuditableEntity} (identity + auditing) and adds domain-event support: call
 * {@link #registerEvent(Object)} from behavior methods; Spring Data publishes the collected events on
 * {@code save()} (detected via {@code @DomainEvents} / {@code @AfterDomainEventPublication}) and other
 * modules consume them with {@code @ApplicationModuleListener}. Re-implementing the tiny event
 * mechanism here (instead of extending Spring's {@code AbstractAggregateRoot}) lets roots and non-root
 * entities share a single {@link AuditableEntity} base — no duplicated id/audit fields.
 * <p>
 * Non-root entities inside an aggregate extend {@link AuditableEntity} directly (no events, no repo).
 */
@MappedSuperclass
public abstract class AggregateRoot extends AuditableEntity {

    @Transient
    private final transient List<Object> domainEvents = new ArrayList<>();

    protected AggregateRoot() {
        super();
    }

    protected AggregateRoot(UUID id) {
        super(id);
    }

    /**
     * Registers a domain event to be published when this aggregate is saved.
     *
     * @param event the event (typically a {@link DomainEvent} record)
     * @param <E>   event type, returned for fluent use
     * @return the same event
     */
    protected <E> E registerEvent(E event) {
        domainEvents.add(event);
        return event;
    }

    @DomainEvents
    Collection<Object> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @AfterDomainEventPublication
    void clearDomainEvents() {
        domainEvents.clear();
    }
}
