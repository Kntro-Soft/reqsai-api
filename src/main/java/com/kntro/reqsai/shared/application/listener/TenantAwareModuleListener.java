package com.kntro.reqsai.shared.application.listener;

import com.kntro.reqsai.shared.domain.model.TenantAwareDomainEvent;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;

/**
 * Base class for {@code @ApplicationModuleListener} methods that need to perform
 * database queries in a multi-tenant setup.
 *
 * <p>Spring Modulith opens its {@code REQUIRES_NEW} transaction before the listener
 * method runs, so the {@link TenantContext} ThreadLocal is lost by that point.
 * Call {@link #withTenant(TenantAwareDomainEvent, Runnable)} to restore the tenant
 * coordinates carried by the event before any DB access.
 *
 * <p>Usage:
 * <pre>{@code
 * class MyListener extends TenantAwareModuleListener {
 *
 *     @ApplicationModuleListener
 *     void onSomethingHappened(MySomethingEvent event) {
 *         withTenant(event, () -> myService.handle(event.aggregateId()));
 *     }
 * }
 * }</pre>
 */
public abstract class TenantAwareModuleListener {

    protected void withTenant(TenantAwareDomainEvent event, Runnable action) {
        TenantContext.runWith(event.tenant(), action);
    }
}
