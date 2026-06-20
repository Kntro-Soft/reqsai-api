package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateProjectCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateProjectCommandHandler {

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;

    @Transactional
    public Project handle(UpdateProjectCommand command) {
        organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        Project project = projects.findById(command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        if (!project.getOrganizationId().equals(command.organizationId())) {
            throw WorkspaceExceptions.projectNotFound(command.projectId());
        }

        if (projects.existsByOrganizationIdAndNameAndIdNot(command.organizationId(), command.name(), command.projectId())) {
            throw WorkspaceExceptions.projectNameAlreadyExists(command.name());
        }

        TechnicalProfile newProfile = new TechnicalProfile(
                command.programmingLanguages(),
                command.frameworks(),
                command.clientPlatforms(),
                command.databases(),
                command.architecture(),
                command.domain()
        );

        project.updateDetails(command.name(), command.description(), newProfile);
        return projects.save(project);
    }
}
