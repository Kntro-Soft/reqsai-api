package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Loads a single organization the requester owns. A non-existent or
 * not-owned organization is reported as not found so ownership is never leaked.
 */
@Component
@RequiredArgsConstructor
public class GetOrganizationQueryHandler {

    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public Organization handle(UUID organizationId, UUID requesterId) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(organizationId));
        if (!organization.getOwnerId().equals(requesterId)) {
            throw WorkspaceExceptions.organizationNotFound(organizationId);
        }
        return organization;
    }
}
