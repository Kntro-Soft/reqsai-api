package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.AddProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AddProjectConstraintCommandHandler {

    private final ProjectRepository projects;

    @Transactional
    public ProjectConstraint handle(AddProjectConstraintCommand command) {
        Project project = projects.findById(command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        ProjectConstraint constraint = project.addConstraint(command.description());
        projects.save(project);
        return constraint;
    }
}
