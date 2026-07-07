package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateProjectCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateProjectCommandHandler {

    private final ProjectRepository projects;

    @Transactional
    public Project handle(UpdateProjectCommand command) {
        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        if (projects.existsByOrganizationIdAndNameAndIdNotAndStatus(
                command.organizationId(), command.name(), command.projectId(), ProjectStatus.ACTIVE)) {
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
