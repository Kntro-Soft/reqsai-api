package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.ArchiveProjectCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ArchiveProjectCommandHandler {

    private final ProjectRepository projects;

    @Transactional
    public void handle(ArchiveProjectCommand command) {
        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));
        project.archive();
        projects.save(project);
    }
}
