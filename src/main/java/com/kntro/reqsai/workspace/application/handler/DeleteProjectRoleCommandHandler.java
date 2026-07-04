package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.DeleteProjectRoleCommand;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
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
public class DeleteProjectRoleCommandHandler {

    private final ProjectRepository projects;
    private final ProjectRoleRepository roles;
    private final ProjectMemberRepository members;

    @Transactional
    public void handle(DeleteProjectRoleCommand command) {
        projects.findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        ProjectRole role = roles.findByIdAndProjectId(command.roleId(), command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectRoleNotFound(command.roleId()));

        long assigned = members.countByProjectIdAndRoleId(command.projectId(), command.roleId());
        if (assigned > 0) {
            throw WorkspaceExceptions.projectRoleInUse(command.roleId(), assigned);
        }

        roles.delete(role);
    }
}
