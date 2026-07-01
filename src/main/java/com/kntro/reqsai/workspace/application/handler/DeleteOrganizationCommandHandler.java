package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.ProvisioningService;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.workspace.application.command.DeleteOrganizationCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deletes an organization (owner-only). Soft-deletes the registry row ({@code status = DELETED}) — matching
 * the aggregate's lifecycle convention and so the resolver stops mapping the tenant — then deprovisions the
 * tenant schema (drops {@code tenant_<slug>}). The registry row is intentionally retained (audit/history)
 * rather than hard-deleted; only the tenant data is physically removed.
 * <p>
 * Deliberately not {@code @Transactional}: the schema drop (DDL) must not sit inside a JPA transaction, so
 * this mirrors {@code CreateOrganizationCommandHandler}. The soft-delete is persisted first; a later drop
 * failure leaves a DELETED org (harmless — the resolver already excludes it) rather than an active org with
 * no schema.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteOrganizationCommandHandler {

    private final OrganizationRepository organizations;
    private final ProvisioningService provisioningService;
    private final TenantSchemaResolver tenantSchemaResolver;

    public void handle(DeleteOrganizationCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        if (!organization.getOwnerId().equals(command.requestedBy())) {
            throw WorkspaceExceptions.organizationEditPermissionDenied(command.organizationId(), command.requestedBy());
        }

        organization.delete();
        organizations.save(organization);

        String slug = organization.getSlug().value();
        tenantSchemaResolver.evictTenantSchema(organization.getId().toString());
        provisioningService.deprovisionTenant(slug);
        log.info("Organization {} deleted and tenant schema for slug {} deprovisioned", organization.getId(), slug);
    }
}
