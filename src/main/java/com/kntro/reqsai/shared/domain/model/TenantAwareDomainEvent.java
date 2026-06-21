package com.kntro.reqsai.shared.domain.model;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;

/**
 * Extension of {@link DomainEvent} for events whose {@code @ApplicationModuleListener} consumers
 * need to perform database queries in a multi-tenant schema-per-tenant setup.
 *
 * <p>Carry a {@link TenantContext.TenantSnapshot} captured at event creation time (originating
 * thread) so the global {@code TenantContextListenerAspect} can restore the context automatically
 * in the async listener thread — no tenant boilerplate in any listener.
 *
 * <p>In each event's factory method call {@link TenantContext#capture()} once and store the result
 * as a single {@code tenant} field on the record.
 */
public interface TenantAwareDomainEvent extends DomainEvent {

    TenantContext.TenantSnapshot tenant();
}
