package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.GetMyProjectPermissionsQuery;
import com.kntro.reqsai.workspace.application.service.ProjectPermissionService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves the caller's effective permissions on a project. The organization is taken from the tenant
 * bound to the request (routes carry no {@code orgId}); the effective set is the union of the base
 * floor and the caller's project role (all permissions for owners/admins).
 */
@Component
@RequiredArgsConstructor
public class GetMyProjectPermissionsQueryHandler {

    private final OrganizationRepository organizations;
    private final ProjectPermissionService projectPermissions;

    @Transactional(readOnly = true)
    public Set<Permission> handle(GetMyProjectPermissionsQuery query) {
        UUID orgId = currentTenantOrgId();
        if (orgId == null) {
            throw WorkspaceExceptions.insufficientPermissions(
                    "read project permissions", query.requestedBy());
        }
        Organization organization = organizations.findById(orgId)
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(orgId));

        return projectPermissions.effectivePermissions(organization, query.projectId(), query.requestedBy());
    }

    private static UUID currentTenantOrgId() {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            return null;
        }
        try {
            return UUID.fromString(tenant);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
