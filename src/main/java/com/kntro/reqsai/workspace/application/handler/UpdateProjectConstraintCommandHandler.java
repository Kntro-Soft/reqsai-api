package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateProjectConstraintCommandHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;

    @Transactional
    public ProjectConstraint handle(UpdateProjectConstraintCommand command) {
        organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        ProjectConstraint constraint = project.updateConstraint(command.constraintId(), command.description());
        projects.save(project);
        return constraint;
    }
}
