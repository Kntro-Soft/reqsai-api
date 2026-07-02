package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateProjectMemberCommand;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateProjectMemberCommandHandler {

    private final ProjectRepository projects;
    private final ProjectRoleRepository roles;
    private final ProjectMemberRepository assignments;

    @Transactional
    public ProjectMember handle(UpdateProjectMemberCommand command) {
        projects.findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        roles.findByIdAndProjectId(command.roleId(), command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectRoleNotFound(command.roleId()));

        ProjectMember assignment = assignments.findByIdAndProjectId(command.assignmentId(), command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectMemberNotFound(command.assignmentId()));
        assignment.changeRole(command.roleId());
        return assignments.save(assignment);
    }
}
