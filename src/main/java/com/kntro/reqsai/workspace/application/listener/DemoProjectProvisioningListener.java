package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.workspace.application.service.DemoProjectSeeder;
import com.kntro.reqsai.workspace.domain.event.OrganizationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gives every new organization its demo project (US28) as soon as its tenant schema is provisioned and the
 * organization is {@code ACTIVE}.
 * <p>
 * Listens to {@link OrganizationCreatedEvent} <strong>synchronously after commit</strong> (default
 * {@code AFTER_COMMIT} phase; {@code fallbackExecution} also covers a publication outside a transaction),
 * so the organization row is already committed as {@code ACTIVE} — the schema resolver only maps active
 * organizations — and the {@code POST /api/organizations} response is sent once the demo exists: the first
 * project list after onboarding already shows it. Seeding is a handful of inserts with no AI call, so it
 * adds negligible latency.
 * <p>
 * The organization-creation request is bound to the caller's previous tenant (if any), so the new tenant is
 * bound explicitly around the seeding and the previous binding restored afterwards. Best-effort: a seeding
 * failure is logged and never fails the organization creation (the demo can still be restored later).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class DemoProjectProvisioningListener {

    private final DemoProjectSeeder seeder;
    private final TenantSchemaResolver tenantSchemaResolver;

    @TransactionalEventListener(fallbackExecution = true)
    void onOrganizationCreated(OrganizationCreatedEvent event) {
        String tenantId = event.organizationId().toString();
        String schema = tenantSchemaResolver.resolveTenantSchema(tenantId);
        if (TenantContext.DEFAULT_SCHEMA.equals(schema)) {
            log.warn("No tenant schema resolved for new organization {}; demo project not seeded", tenantId);
            return;
        }

        String previousTenant = TenantContext.getCurrentTenant();
        String previousSchema = TenantContext.getCurrentSchema();
        try {
            TenantContext.runWith(new TenantContext.TenantSnapshot(tenantId, schema),
                    () -> seeder.provision(event.organizationId(), event.ownerId()));
        } catch (RuntimeException e) {
            log.error("Demo project could not be seeded for organization {}", tenantId, e);
        } finally {
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
                TenantContext.setCurrentSchema(previousSchema);
            }
        }
    }
}
