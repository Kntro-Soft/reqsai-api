package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.CreateProjectRoleCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateProjectRoleCommandHandler {

    private final ProjectRepository projects;
    private final ProjectRoleRepository roles;

    @Transactional
    public ProjectRole handle(CreateProjectRoleCommand command) {
        projects.findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        String normalizedName = ProjectRole.normalizeName(command.name());
        if (roles.existsByProjectIdAndName(command.projectId(), normalizedName)) {
            throw WorkspaceExceptions.projectRoleNameAlreadyExists(normalizedName);
        }

        return roles.save(new ProjectRole(command.projectId(), normalizedName, command.permissions()));
    }
}
