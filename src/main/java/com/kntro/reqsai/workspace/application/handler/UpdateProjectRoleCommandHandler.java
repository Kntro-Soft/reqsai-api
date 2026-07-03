package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateProjectRoleCommand;
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
public class UpdateProjectRoleCommandHandler {

    private final ProjectRepository projects;
    private final ProjectRoleRepository roles;

    @Transactional
    public ProjectRole handle(UpdateProjectRoleCommand command) {
        projects.findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        ProjectRole role = roles.findByIdAndProjectId(command.roleId(), command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectRoleNotFound(command.roleId()));

        String normalizedName = ProjectRole.normalizeName(command.name());
        if (roles.existsByProjectIdAndNameAndIdNot(command.projectId(), normalizedName, command.roleId())) {
            throw WorkspaceExceptions.projectRoleNameAlreadyExists(normalizedName);
        }

        role.update(normalizedName, command.permissions());
        return roles.save(role);
    }
}
