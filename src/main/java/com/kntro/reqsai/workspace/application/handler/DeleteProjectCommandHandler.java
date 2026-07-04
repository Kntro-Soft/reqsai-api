package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.DeleteProjectCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteProjectCommandHandler {

    private final ProjectRepository projects;

    @Transactional
    public void handle(DeleteProjectCommand command) {
        Project project = projects.findByIdAndOrganizationId(command.projectId(), command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));
        projects.delete(project);
    }
}
