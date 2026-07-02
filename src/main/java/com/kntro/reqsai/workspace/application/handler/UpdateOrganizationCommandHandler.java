package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateOrganizationCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateOrganizationCommandHandler {

    private final OrganizationRepository organizations;

    @Transactional
    public Organization handle(UpdateOrganizationCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        organization.applyPatch(command.name(), command.meetingLanguage(), command.audioRetentionDays());

        return organizations.save(organization);
    }
}
