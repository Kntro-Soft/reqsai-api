package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.DeleteProjectRoleCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteProjectRoleCommandHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectRoleRepository roles;
    private final OrganizationAdminAccessService access;

    @Transactional
    public void handle(DeleteProjectRoleCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));
        access.assertOwnerOrAdmin(organization, command.requestedBy(), "manage project roles");

        projects.findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        ProjectRole role = roles.findByIdAndProjectId(command.roleId(), command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectRoleNotFound(command.roleId()));
        roles.delete(role);
    }
}
