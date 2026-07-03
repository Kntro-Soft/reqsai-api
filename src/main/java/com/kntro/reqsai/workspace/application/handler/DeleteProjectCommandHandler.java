package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.DeleteProjectCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteProjectCommandHandler {

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;
    private final OrganizationAdminAccessService orgAccess;

    @Transactional
    public void handle(DeleteProjectCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));
        orgAccess.assertOwnerOrAdmin(organization, command.requestedBy(), "delete project");

        Project project = projects.findByIdAndOrganizationId(command.projectId(), command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));
        projects.delete(project);
    }
}
