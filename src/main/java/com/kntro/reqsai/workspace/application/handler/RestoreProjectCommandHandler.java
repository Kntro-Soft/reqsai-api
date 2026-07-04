package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.RestoreProjectCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RestoreProjectCommandHandler {

    private final ProjectRepository projects;

    @Transactional
    public void handle(RestoreProjectCommand command) {
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
