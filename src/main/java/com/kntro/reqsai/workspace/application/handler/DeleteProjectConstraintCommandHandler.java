package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.DeleteProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteProjectConstraintCommandHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;

    @Transactional
    public void handle(DeleteProjectConstraintCommand command) {
        organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        project.removeConstraint(command.constraintId());
        projects.save(project);
    }
}
