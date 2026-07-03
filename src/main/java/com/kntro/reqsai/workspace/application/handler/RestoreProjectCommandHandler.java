package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.RestoreProjectCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.service.ProjectPermissionService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RestoreProjectCommandHandler {

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;
    private final ProjectPermissionService projectPermission;

    @Transactional
    public void handle(RestoreProjectCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));
        projectPermission.assertHasProjectPermission(
                organization, command.projectId(), command.requestedBy(), Permission.WRITE_PROJECT, "restore project");

        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ARCHIVED)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        if (projects.existsByOrganizationIdAndNameAndIdNotAndStatus(
                command.organizationId(), project.getName(), command.projectId(), ProjectStatus.ACTIVE)) {
            throw WorkspaceExceptions.projectNameAlreadyExists(project.getName());
        }

        project.activate();
        projects.save(project);
    }
}
