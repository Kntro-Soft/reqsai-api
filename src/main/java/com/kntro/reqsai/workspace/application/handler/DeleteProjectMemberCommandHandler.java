package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.DeleteProjectMemberCommand;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteProjectMemberCommandHandler {

    private final ProjectRepository projects;
    private final ProjectMemberRepository assignments;

    @Transactional
    public void handle(DeleteProjectMemberCommand command) {
        projects.findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        ProjectMember assignment = assignments.findByIdAndProjectId(command.assignmentId(), command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectMemberNotFound(command.assignmentId()));
        assignments.delete(assignment);
    }
}
