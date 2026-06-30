package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateOrganizationAvatarCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces an organization's avatar with an uploaded image. Only the organization owner or an active
 * admin member may do so.
 */
@Component
@RequiredArgsConstructor
public class UpdateOrganizationAvatarCommandHandler {

    private final OrganizationRepository organizations;
    private final OrganizationAdminAccessService adminAccess;

    @Transactional
    public Organization handle(UpdateOrganizationAvatarCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        adminAccess.assertOwnerOrAdmin(organization, command.requestedBy(), "update organization avatar");

        organization.applyAvatar(command.bytes(), command.contentType());
        return organizations.save(organization);
    }
}
