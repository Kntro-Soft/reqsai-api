package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.GetOrganizationQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetOrganizationQueryHandler {

    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public Organization handle(GetOrganizationQuery query) {
        Organization organization = organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        if (!organization.getOwnerId().equals(query.requestedBy())) {
            throw WorkspaceExceptions.organizationEditPermissionDenied(query.organizationId(), query.requestedBy());
        }

        return organization;
    }
}
