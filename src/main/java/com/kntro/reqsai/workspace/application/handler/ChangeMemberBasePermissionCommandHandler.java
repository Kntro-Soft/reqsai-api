package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.ChangeMemberBasePermissionCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChangeMemberBasePermissionCommandHandler {

    private final OrganizationRepository organizations;

    @Transactional
    public Organization handle(ChangeMemberBasePermissionCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        organization.changeMemberBasePermission(command.basePermission());

        return organizations.save(organization);
    }
}
